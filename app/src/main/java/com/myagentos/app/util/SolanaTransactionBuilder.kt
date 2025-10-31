package com.myagentos.app.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Utility class for building Solana transactions manually
 * Builds transactions following Solana's wire format specification
 */
object SolanaTransactionBuilder {
    
    // System Program ID (base58: 11111111111111111111111111111111)
    private val SYSTEM_PROGRAM_ID = ByteArray(32) // All zeros
    
    // System Program Transfer instruction discriminator
    private const val TRANSFER_DISCRIMINATOR: Byte = 2
    
    /**
     * Decode a base58 encoded address to byte array
     */
    fun decodeBase58(address: String): ByteArray {
        // System Program ID (base58: 11111111111111111111111111111111) decodes to all zeros
        if (address == "11111111111111111111111111111111") {
            return ByteArray(32)
        }
        
        val decoded = Base58Encoder.decode(address)
        // Ensure it's 32 bytes (pad or truncate)
        return if (decoded.size == 32) {
            decoded
        } else if (decoded.size < 32) {
            ByteArray(32 - decoded.size) + decoded
        } else {
            decoded.sliceArray(decoded.size - 32 until decoded.size)
        }
    }
    
    /**
     * Build a SOL transfer transaction
     * Based on Solana's transaction format:
     * - Message header
     * - Account addresses
     * - Recent blockhash
     * - Instructions
     */
    fun buildSOLTransfer(
        fromPublicKey: ByteArray,
        toPublicKey: ByteArray,
        lamports: Long,
        recentBlockhash: String
    ): ByteArray {
        val blockhashBytes = decodeBase58(recentBlockhash)
        
        // Build the instruction
        val transferInstruction = buildTransferInstruction(
            fromPublicKey = fromPublicKey,
            toPublicKey = toPublicKey,
            lamports = lamports
        )
        
        // Build the message
        val message = buildMessage(
            payerAccount = fromPublicKey,
            instructions = listOf(transferInstruction),
            recentBlockhash = blockhashBytes
        )
        
        // Transaction format: signatures (will be filled by wallet) + message
        // For now, we create message with placeholder signature slots
        // The wallet will add the actual signatures
        
        // Message needs to be serialized
        // Transaction = Compact array of signatures (1 byte length, then signatures) + message bytes
        val messageBytes = message
        
        // Transaction structure:
        // - signatures: u8 (number of signatures) + signature data (64 bytes each)
        // - message: serialized message
        
        // Since we're not signing here, we need 1 signature placeholder
        val signatureCount: Byte = 1
        val signaturePlaceholder = ByteArray(64) // Will be filled by wallet
        
        // Build final transaction
        val transaction = ByteArray(1 + 64 + messageBytes.size)
        var offset = 0
        
        // Write signature count
        transaction[offset++] = signatureCount
        
        // Write signature placeholder (will be replaced by wallet)
        System.arraycopy(signaturePlaceholder, 0, transaction, offset, 64)
        offset += 64
        
        // Write message
        System.arraycopy(messageBytes, 0, transaction, offset, messageBytes.size)
        
        return transaction
    }
    
    /**
     * Build a System Program transfer instruction
     */
    private fun buildTransferInstruction(
        fromPublicKey: ByteArray,
        toPublicKey: ByteArray,
        lamports: Long
    ): Instruction {
        // Transfer instruction format:
        // - program_id_index: u8
        // - accounts: compact array of account indices
        // - data: compact array of bytes (instruction discriminator + lamports)
        
        val instructionData = ByteBuffer.allocate(9).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put(TRANSFER_DISCRIMINATOR) // Instruction discriminator
            putLong(lamports) // Amount in lamports
        }.array()
        
        return Instruction(
            programIdIndex = 0, // System program is at index 0
            accounts = listOf(0, 1), // from (0) and to (1) account indices
            data = instructionData
        )
    }
    
    /**
     * Build a Solana message
     */
    private fun buildMessage(
        payerAccount: ByteArray,
        instructions: List<Instruction>,
        recentBlockhash: ByteArray
    ): ByteArray {
        // Message format:
        // - Header (3 bytes: numRequiredSignatures, numReadonlySignedAccounts, numReadonlyUnsignedAccounts)
        // - Account addresses (compact array)
        // - Recent blockhash (32 bytes)
        // - Instructions (compact array)
        
        val accounts = mutableListOf<ByteArray>()
        accounts.add(payerAccount) // Index 0: payer
        accounts.add(decodeBase58("11111111111111111111111111111111")) // Index 1: System Program
        
        // Add any additional accounts from instructions
        instructions.forEach { instruction ->
            instruction.accounts.forEach { accountIndex ->
                if (accountIndex < accounts.size) {
                    // Account already in list
                }
            }
        }
        
        // Build header
        val header = byteArrayOf(
            1, // numRequiredSignatures (1: the payer)
            0, // numReadonlySignedAccounts
            1  // numReadonlyUnsignedAccounts (System Program)
        )
        
        // Serialize accounts
        val accountsBytes = serializeCompactArray(accounts)
        
        // Serialize instructions
        val instructionsBytes = serializeInstructions(instructions, accounts.size)
        
        // Combine: header + accounts + blockhash + instructions
        val messageSize = header.size + accountsBytes.size + recentBlockhash.size + instructionsBytes.size
        val message = ByteArray(messageSize)
        var offset = 0
        
        // Write header
        System.arraycopy(header, 0, message, offset, header.size)
        offset += header.size
        
        // Write accounts
        System.arraycopy(accountsBytes, 0, message, offset, accountsBytes.size)
        offset += accountsBytes.size
        
        // Write blockhash
        System.arraycopy(recentBlockhash, 0, message, offset, recentBlockhash.size)
        offset += recentBlockhash.size
        
        // Write instructions
        System.arraycopy(instructionsBytes, 0, message, offset, instructionsBytes.size)
        
        return message
    }
    
    /**
     * Serialize compact array (length-prefixed)
     */
    private fun serializeCompactArray(items: List<ByteArray>): ByteArray {
        // Compact array format: u16 length + items
        val length = items.size
        
        val totalSize = items.sumOf { it.size }
        val result = ByteBuffer.allocate(2 + totalSize).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            putShort(length.toShort())
        }
        
        items.forEach { item ->
            result.put(item)
        }
        
        return result.array()
    }
    
    /**
     * Serialize instructions
     */
    private fun serializeInstructions(instructions: List<Instruction>, numAccounts: Int): ByteArray {
        // Compact array of instructions - using compact-u16 encoding
        val instructionsData = mutableListOf<ByteArray>()
        instructions.forEach { instruction ->
            // Instruction format: program_id_index + compact array of account indices + compact array of data
            val data = instruction.data
            
            // Build account indices compact array
            val accountIndicesBytes = serializeCompactU16Array(instruction.accounts.map { it.toShort() })
            
            // Build data compact array - data is already ByteArray, encode as compact-u16
            val dataLengthBytes = encodeCompactU16(data.size)
            val dataBytes = ByteArray(dataLengthBytes.size + data.size).apply {
                System.arraycopy(dataLengthBytes, 0, this, 0, dataLengthBytes.size)
                System.arraycopy(data, 0, this, dataLengthBytes.size, data.size)
            }
            
            val instructionSize = 1 + accountIndicesBytes.size + dataBytes.size
            
            val instructionBytes = ByteBuffer.allocate(instructionSize).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                put(instruction.programIdIndex.toByte())
                put(accountIndicesBytes)
                put(dataBytes)
            }.array()
            
            instructionsData.add(instructionBytes)
        }
        
        // Compact-u16 array of instructions
        val lengthBytes = encodeCompactU16(instructions.size)
        val totalSize = lengthBytes.size + instructionsData.sumOf { it.size }
        val result = ByteBuffer.allocate(totalSize).apply {
            put(lengthBytes)
        }
        
        instructionsData.forEach { result.put(it) }
        
        return result.array()
    }
    
    /**
     * Encode a number as compact-u16 (used for array lengths in Solana)
     */
    private fun encodeCompactU16(value: Int): ByteArray {
        return when {
            value <= 0x7F -> byteArrayOf(value.toByte())
            value <= 0x3FFF -> {
                val bytes = ByteArray(2)
                bytes[0] = ((value and 0x7F) or 0x80).toByte()
                bytes[1] = ((value shr 7) and 0xFF).toByte()
                bytes
            }
            else -> {
                val bytes = ByteArray(3)
                bytes[0] = ((value and 0x7F) or 0x80).toByte()
                bytes[1] = (((value shr 7) and 0x7F) or 0x80).toByte()
                bytes[2] = ((value shr 14) and 0xFF).toByte()
                bytes
            }
        }
    }
    
    /**
     * Serialize compact-u16 array
     */
    private fun serializeCompactU16Array(items: List<Short>): ByteArray {
        val lengthBytes = encodeCompactU16(items.size)
        val data = ByteBuffer.allocate(lengthBytes.size + items.size).apply {
            put(lengthBytes)
            items.forEach { putShort(it) }
        }.array()
        return data
    }
    
    
    /**
     * Helper data class for instructions
     */
    private data class Instruction(
        val programIdIndex: Int,
        val accounts: List<Int>,
        val data: ByteArray
    )
}

