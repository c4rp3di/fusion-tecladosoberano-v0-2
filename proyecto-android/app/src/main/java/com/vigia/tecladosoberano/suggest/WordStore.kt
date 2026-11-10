package com.vigia.tecladosoberano.suggest

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Palabras aprendidas: SOLO palabras que no están en el diccionario (nombres, jerga) y con su recuento.
 * Nunca frases, ni orden, ni contexto, ni palabras del diccionario.
 *
 * Cifrado AES-256-GCM con clave del Android Keystore, archivo privado, sin copias de seguridad.
 * Si el cifrado falla, vive solo en memoria. No se aprende en campos de contraseña (lo decide quien llama).
 */
class WordStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val counts = HashMap<String, Int>()

    init {
        load()
    }

    fun count(): Int = counts.size

    /** Palabras aprendidas que empiezan por [prefijoNorm] (ya normalizado), con su recuento. */
    fun conPrefijo(prefijoNorm: String): List<Pair<String, Int>> {
        val r = ArrayList<Pair<String, Int>>()
        for ((w, n) in counts) if (Dictionary.normalizar(w).startsWith(prefijoNorm)) r.add(w to n)
        return r
    }

    fun learn(palabra: String) {
        val w = palabra.lowercase()
        if (w.length < MIN_LEN || w.length > MAX_LEN) return
        counts[w] = (counts[w] ?: 0) + 1
        if (counts.size > MAX_WORDS) {
            // Se descartan las menos usadas.
            val sobrantes = counts.entries.sortedBy { it.value }.take(counts.size - MAX_WORDS + 100).map { it.key }
            for (k in sobrantes) counts.remove(k)
        }
        save()
    }

    fun forget(palabra: String) {
        if (counts.remove(palabra.lowercase()) != null) save()
    }

    fun clear() {
        counts.clear()
        file.delete()
    }

    // ------------------------------------------------------------------ cifrado

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existente = ks.getKey(ALIAS, null)
        if (existente is SecretKey) return existente
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun save() {
        try {
            if (counts.isEmpty()) {
                file.delete()
                return
            }
            val json = JSONObject()
            for ((w, n) in counts) json.put(w, n)
            val claro = json.toString().toByteArray(Charsets.UTF_8)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val cifrado = cipher.doFinal(claro)
            claro.fill(0)

            val salida = ByteArray(1 + iv.size + cifrado.size)
            salida[0] = iv.size.toByte()
            System.arraycopy(iv, 0, salida, 1, iv.size)
            System.arraycopy(cifrado, 0, salida, 1 + iv.size, cifrado.size)

            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeBytes(salida)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            // Sin persistencia segura: solo en memoria, nunca en claro.
        }
    }

    private fun load() {
        try {
            if (!file.exists()) return
            val datos = file.readBytes()
            val ivLen = datos[0].toInt()
            if (ivLen < 12 || datos.size <= 1 + ivLen) throw IllegalStateException("formato")
            val iv = datos.copyOfRange(1, 1 + ivLen)
            val cifrado = datos.copyOfRange(1 + ivLen, datos.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val claro = cipher.doFinal(cifrado)
            val json = JSONObject(String(claro, Charsets.UTF_8))
            claro.fill(0)
            val it = json.keys()
            while (it.hasNext()) {
                val w = it.next()
                counts[w] = json.optInt(w, 1)
            }
        } catch (e: Exception) {
            counts.clear()
            file.delete()
        }
    }

    private companion object {
        const val FILE_NAME = "words.dat"
        const val ALIAS = "ks_words_key"
        const val MAX_WORDS = 1500
        const val MIN_LEN = 3
        const val MAX_LEN = 24
    }
}
