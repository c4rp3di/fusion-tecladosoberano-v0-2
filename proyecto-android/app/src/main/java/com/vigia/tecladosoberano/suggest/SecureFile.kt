package com.vigia.tecladosoberano.suggest

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Archivo privado cifrado con AES-256-GCM. La clave vive en el Android Keystore y no sale del dispositivo.
 * Si algo falla, no se escribe nada en claro.
 */
class SecureFile(context: Context, nombre: String, private val alias: String) {

    private val file = File(context.applicationContext.filesDir, nombre)
    private val tmp = File(context.applicationContext.filesDir, "$nombre.tmp")

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existente = ks.getKey(alias, null)
        if (existente is SecretKey) return existente
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    /** Contenido descifrado, o null si no existe o está dañado (en ese caso se borra). */
    fun leer(): ByteArray? {
        try {
            if (!file.exists()) return null
            val datos = file.readBytes()
            val ivLen = datos[0].toInt()
            if (ivLen < 12 || datos.size <= 1 + ivLen) throw IllegalStateException("formato")
            val iv = datos.copyOfRange(1, 1 + ivLen)
            val cifrado = datos.copyOfRange(1 + ivLen, datos.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            return cipher.doFinal(cifrado)
        } catch (e: Exception) {
            file.delete()
            return null
        }
    }

    /** Guarda [claro] cifrado. Con null borra el archivo. */
    fun escribir(claro: ByteArray?) {
        try {
            if (claro == null) {
                file.delete()
                return
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val cifrado = cipher.doFinal(claro)
            val salida = ByteArray(1 + iv.size + cifrado.size)
            salida[0] = iv.size.toByte()
            System.arraycopy(iv, 0, salida, 1, iv.size)
            System.arraycopy(cifrado, 0, salida, 1 + iv.size, cifrado.size)
            tmp.writeBytes(salida)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            // Sin persistencia segura: se queda solo en memoria.
        }
    }
}
