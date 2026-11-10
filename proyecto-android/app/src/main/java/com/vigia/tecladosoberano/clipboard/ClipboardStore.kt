package com.vigia.tecladosoberano.clipboard

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Historial del portapapeles: los últimos [MAX_ITEMS] textos, cifrados en disco.
 *
 * - Cifrado AES-256-GCM con una clave que vive en el Android Keystore (no sale del chip de seguridad / TEE).
 * - Archivo en el almacenamiento privado de la app; sin copias de seguridad (allowBackup=false).
 * - Si el cifrado falla por cualquier motivo, NO se guarda nada en claro: el historial vive solo en memoria.
 */
class ClipboardStore(context: Context) {

    class Item(val text: String, val time: Long)

    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val items = ArrayList<Item>()

    init {
        load()
    }

    /** Copia de la lista, del más reciente al más antiguo. */
    fun all(): List<Item> = ArrayList(items)

    /** Devuelve true si el historial cambió. Un texto repetido sube al principio. */
    fun add(text: String): Boolean {
        if (text.isBlank() || text.length > MAX_LEN) return false
        if (items.isNotEmpty() && items[0].text == text) return false
        items.removeAll { it.text == text }
        items.add(0, Item(text, System.currentTimeMillis()))
        while (items.size > MAX_ITEMS) items.removeAt(items.size - 1)
        save()
        return true
    }

    fun remove(index: Int) {
        if (index !in items.indices) return
        items.removeAt(index)
        save()
    }

    fun clear() {
        items.clear()
        save()
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
            if (items.isEmpty()) {
                file.delete()
                return
            }
            val json = JSONArray()
            for (i in items) json.put(JSONObject().put("t", i.text).put("ts", i.time))
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
            // Sin persistencia segura: se mantiene solo en memoria, nunca en claro.
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
            val json = JSONArray(String(claro, Charsets.UTF_8))
            claro.fill(0)
            for (n in 0 until json.length()) {
                val o = json.getJSONObject(n)
                items.add(Item(o.getString("t"), o.optLong("ts")))
            }
        } catch (e: Exception) {
            // Archivo dañado o clave invalidada: se descarta el historial.
            items.clear()
            file.delete()
        }
    }

    private companion object {
        const val FILE_NAME = "clip.dat"
        const val ALIAS = "ks_clip_key"
        const val MAX_ITEMS = 25
        const val MAX_LEN = 5000
    }
}
