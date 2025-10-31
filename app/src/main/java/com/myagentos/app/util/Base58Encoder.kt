package com.myagentos.app.util

import java.math.BigInteger

/**
 * Base58 encoding utility for Solana public keys
 */
object Base58Encoder {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    
    fun encodeToString(input: ByteArray): String {
        if (input.isEmpty()) return ""
        
        // Count leading zeros
        var zeros = 0
        while (zeros < input.size && input[zeros].toInt() == 0) {
            zeros++
        }
        
        // Convert to base58
        val encoded = CharArray(input.size * 2)
        var outputStart = encoded.size
        var inputStart = zeros
        
        while (inputStart < input.size) {
            encoded[--outputStart] = ALPHABET[divmod(input, inputStart, 256, 58)]
            if (input[inputStart].toInt() == 0) {
                inputStart++
            }
        }
        
        // Skip leading zeros in the output
        while (outputStart < encoded.size && encoded[outputStart] == ALPHABET[0]) {
            outputStart++
        }
        
        // Add leading '1' for each leading zero byte
        while (--zeros >= 0) {
            encoded[--outputStart] = ALPHABET[0]
        }
        
        return String(encoded, outputStart, encoded.size - outputStart)
    }
    
    private fun divmod(number: ByteArray, firstDigit: Int, base: Int, divisor: Int): Int {
        var remainder = 0
        for (i in firstDigit until number.size) {
            val digit = number[i].toInt() and 0xFF
            val temp = remainder * base + digit
            number[i] = (temp / divisor).toByte()
            remainder = temp % divisor
        }
        return remainder
    }
    
    /**
     * Decode a base58 string to byte array
     */
    fun decode(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        
        var num = BigInteger.ZERO
        var leadingZeros = 0
        val base = BigInteger.valueOf(58)
        
        for (i in input.indices) {
            if (input[i] != ALPHABET[0]) break
            leadingZeros++
        }
        
        for (i in leadingZeros until input.length) {
            val charIndex = ALPHABET.indexOf(input[i])
            if (charIndex == -1) throw IllegalArgumentException("Invalid Base58 character: ${input[i]}")
            num = num.multiply(base).add(BigInteger.valueOf(charIndex.toLong()))
        }
        
        val decoded = num.toByteArray()
        val trimmed = if (decoded[0] == 0.toByte()) decoded.sliceArray(1 until decoded.size) else decoded
        
        return ByteArray(leadingZeros) { 0 } + trimmed
    }
}
