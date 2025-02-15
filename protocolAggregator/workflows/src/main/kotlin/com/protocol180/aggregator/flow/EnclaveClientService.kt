package com.protocol180.aggregator.flow

import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import org.apache.avro.Schema
import org.apache.avro.file.DataFileReader
import org.apache.avro.file.DataFileWriter
import org.apache.avro.file.SeekableByteArrayInput
import org.apache.avro.file.SeekableInput
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.DatumReader
import org.apache.avro.io.DatumWriter
import org.apache.avro.io.EncoderFactory
import java.io.*
import java.lang.Boolean
import java.util.*
import java.util.function.Consumer
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * A utility service to provide functionality of avro serialization, creating data input for providers and
 * generating response records from decrypted output
 */
@CordaService
class EnclaveClientService(val services: AppServiceHub) : SingletonSerializeAsToken() {


    var envelopeSchema: Schema? = null
    var aggregationInputSchema: Schema? = null
    var aggregationOutputSchema: Schema? = null
    var rewardsOutputSchema: Schema? = null

    fun initializeSchema(envelopeSchema: String) {
        val envelopeSchema = try {
            Schema.Parser().parse(envelopeSchema)
        } catch (e: Exception) {
            throw IllegalArgumentException("Wrong schema provided for data aggregation.")
        }
        aggregationInputSchema = envelopeSchema!!.getField("aggregateInput").schema()
        aggregationOutputSchema = envelopeSchema!!.getField("aggregateOutput").schema()
        rewardsOutputSchema = envelopeSchema!!.getField("rewardsOutput").schema()
    }


    fun createProviderDataRecordForAggregation(headerLine: String, lineList: MutableList<String>): ByteArray? {
        //create generic records using avro schema for aggregation and append to file
        val genericRecords = ArrayList<GenericRecord>()
        val headers = headerLine.split(",")
        lineList.forEach() {
            val demandRecord: GenericRecord = GenericData.Record(aggregationInputSchema)
            val dataValues = it.split(",")
            headers.forEachIndexed {
                index, value ->
                val schemaType = aggregationInputSchema!!.getField(value).schema().type;
                val parsedValue = parseValuesToSchemaType(dataValues[index], schemaType);
                demandRecord.put(value, parsedValue);
            }
            genericRecords.add(demandRecord)
        }
        return createAvroDataFileFromGenericRecords(genericRecords)
    }

    private fun parseValuesToSchemaType(dataValues: String, schemaType: Schema.Type): Any {
        when (schemaType) {
            Schema.Type.STRING -> return dataValues.trim { it <= ' ' }
            Schema.Type.BYTES -> return dataValues.trim { it <= ' ' }.toByte()
            Schema.Type.INT -> return dataValues.trim { it <= ' ' }.toInt()
            Schema.Type.LONG -> return dataValues.trim { it <= ' ' }.toLong()
            Schema.Type.FLOAT -> return dataValues.trim { it <= ' ' }.toFloat()
            Schema.Type.DOUBLE -> return dataValues.trim { it <= ' ' }.toDouble()
            Schema.Type.BOOLEAN -> return dataValues.trim { it <= ' ' }.toBoolean()
        }

        return dataValues;
    }

    private fun createAvroDataFileFromGenericRecords(genericRecords: ArrayList<GenericRecord>): ByteArray {
        val datumWriter: DatumWriter<GenericRecord> = GenericDatumWriter(aggregationInputSchema)
        val dataFileWriter = DataFileWriter(datumWriter)
        val byteArrayOutputStream = ByteArrayOutputStream()
        dataFileWriter.create(aggregationInputSchema, byteArrayOutputStream)
        genericRecords.forEach(Consumer { genericRecord: GenericRecord ->
            try {
                dataFileWriter.append(genericRecord)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        })
        byteArrayOutputStream.close()
        dataFileWriter.close()
        return byteArrayOutputStream.toByteArray()
    }

    fun readGenericRecordsFromOutputBytesAndSchema(outputBytes: ByteArray, schemaType: String): ArrayList<GenericRecord?> {
        val datumReader: DatumReader<GenericRecord> = if (schemaType == "aggregate") GenericDatumReader(aggregationOutputSchema) else GenericDatumReader(rewardsOutputSchema)
        val input: SeekableInput = SeekableByteArrayInput(outputBytes)
        val dataFileReader = DataFileReader(input, datumReader)
        val genericRecords = ArrayList<GenericRecord?>()
        var dataRecord: GenericRecord? = null
        while (dataFileReader.hasNext()) {
            dataRecord = dataFileReader.next()
            genericRecords.add(dataRecord)
        }
        return genericRecords
    }

    fun readJsonFromOutputBytesAndSchema(outputBytes: ByteArray, schemaType: String): ByteArrayOutputStream {
        val schema = if (schemaType == "aggregate") aggregationOutputSchema else rewardsOutputSchema
        val datumReader: DatumReader<GenericRecord> = GenericDatumReader(schema)
        val input: SeekableInput = SeekableByteArrayInput(outputBytes)
        val dataFileReader = DataFileReader(input, datumReader)
        val genericRecords = ArrayList<GenericRecord?>()
        var dataRecord: GenericRecord?
        while (dataFileReader.hasNext()) {
            dataRecord = dataFileReader.next()
            genericRecords.add(dataRecord)
        }
        val outputStream = ByteArrayOutputStream()
        val encoder = EncoderFactory.get().jsonEncoder(schema, outputStream)
        val datumWriter: DatumWriter<GenericRecord> = GenericDatumWriter(schema)
        genericRecords.forEach{
            datumWriter.write(it, encoder)
        }
        encoder.flush()
        return outputStream
    }

    fun readInputDataFromAttachment(zipData: ByteArray): MutableList<String> {
        val attachmentFile = File("provider_data_file.zip")
        attachmentFile.writeBytes(zipData)
        val zipAttachmentFile = ZipFile(attachmentFile)
        val zis = ZipInputStream(FileInputStream(attachmentFile))

        val zipEntry = zis.nextEntry
                ?: throw FileNotFoundException("Input Data CSV file is not available into attachment.")
        val inputStream = zipAttachmentFile.getInputStream(zipEntry)
        val lineList = mutableListOf<String>()

        inputStream.bufferedReader().forEachLine { if (it != "") lineList.add(it) }
        inputStream.close()
        zis.close()

        zipAttachmentFile.close()
        attachmentFile.delete()
        return lineList
    }
}
