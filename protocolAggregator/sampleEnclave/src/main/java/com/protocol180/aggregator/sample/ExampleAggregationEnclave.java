package com.protocol180.aggregator.sample;

import com.protocol180.aggregator.enclave.AggregationEnclave;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumWriter;

import java.io.File;
import java.io.IOException;
import java.security.PublicKey;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The below enclave provides an example of how to write 180Protocol Broker Flow compatible Enclaves.
 * Example enclaves must handle data output and rewards calculations for coalition supported data types. These data
 * types and their associated schemas must be indicated as an Enum inside the Enclave.
 * The 'createRewardsDataOutput' and 'createAggregateDataOutput' methods must be designed to handle computations for each
 * of the supported data types (and their corresponding schemas).
 **/
public class ExampleAggregationEnclave extends AggregationEnclave {

    enum SupportedDataTypes {
        testSchema1
    }

    Random random = new Random();

    ArrayList<String> pivot = new ArrayList<String>() {
        {
            add("month");
            add("brand");
            add("type");
            add("model");
        }
    };

    /**
     * method defined on AggregationEnclave interface that is overridden in the child enclave.
     * Used to calculate rewards for a specific provider.
     * Accepts the key of the provider for which the Rewards computation is done. Future implementations will support
     * calling a Rewards engine that calculates rewards factors automatically and based on regression.
     **/
    protected File createRewardsDataOutput(PublicKey providerKey) throws IOException {
        ArrayList<GenericRecord> clientRecords = clientToRawDataMap.get(providerKey);
        ArrayList<GenericRecord> allRecords = new ArrayList<>();
        clientToRawDataMap.values().forEach(genericRecords -> allRecords.addAll(genericRecords));

        //populate rewards output file here based on raw client data
        File outputFile = new File("rewardsOutput.avro");
        DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(rewardsOutputSchema);

        DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<>(datumWriter);
        dataFileWriter.create(rewardsOutputSchema, outputFile);

        GenericRecord rewardRecord = new GenericData.Record(rewardsOutputSchema);
        float amountProvided = (float) clientRecords.size() / (float) allRecords.size();
        float completeness = (float) groupByModelCountryAndCalculateCount(clientRecords, "model", "country") / (float) groupByModelCountryAndCalculateCount(allRecords, "model", "country");
        float uniqueness = (float) groupByAndCalculateCount(clientRecords, pivot.get(2)) / (float) groupByAndCalculateCount(allRecords, pivot.get(2));
        float updateFrequency = (float) groupByDateAndCalculateCount(clientRecords, "date") / (float) groupByDateAndCalculateCount(allRecords, "date");
        float qualityScore = (amountProvided + completeness + uniqueness + updateFrequency) / 4;
        float rewards = qualityScore * 100;

        switch (SupportedDataTypes.valueOf(envelopeSchema.getName())) {
            case testSchema1:
                rewardRecord.put("amountProvided", amountProvided);
                rewardRecord.put("completeness", completeness);
                rewardRecord.put("uniqueness", uniqueness);
                rewardRecord.put("updateFrequency", updateFrequency);
                rewardRecord.put("qualityScore", qualityScore);
                rewardRecord.put("rewards", rewards);
                rewardRecord.put("dataType", envelopeSchema.getName());
                break;
            default:
                throw new IOException("Envelope Schema contains unsupported data type: " + envelopeSchema.getName());
        }
        try {
            dataFileWriter.append(rewardRecord);
        } catch (IOException e) {
            e.printStackTrace();
        }

        dataFileWriter.close();
        return outputFile;
    }

    /**
     * method defined on AggregationEnclave interface that is overridden in the child enclave.
     * Used to calculate data output for a specific consumer.
     **/
    @Override
    protected File createAggregateDataOutput() throws IOException {
        //populate aggregate logic here based on raw client data and return output file
        convertEncryptedClientDataToRawData();

        ArrayList<GenericRecord> allRecords = new ArrayList<>();
        clientToRawDataMap.values().forEach(genericRecords -> allRecords.addAll(genericRecords));

        File outputFile = new File("aggregateOutput.avro");
        DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(aggregateOutputSchema);
        DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<>(datumWriter);
        dataFileWriter.create(aggregateOutputSchema, outputFile);


        //simple aggregation of records into one file
        //other possibilities include creating a output with a specified schema
        switch (SupportedDataTypes.valueOf(envelopeSchema.getName())) {
            case testSchema1:
                GenericRecord demandRecord = new GenericData.Record(aggregateOutputSchema);
                GenericRecord averagePriceRecord = new GenericData.Record(aggregateOutputSchema.getField("averagePrice").schema());
                GenericRecord unitsSoldRecord = new GenericData.Record(aggregateOutputSchema.getField("unitsSold").schema());
                GenericRecord totalSalesRecord = new GenericData.Record(aggregateOutputSchema.getField("totalSales").schema());
                averagePriceRecord.put("pivotId", pivot.get(3));
                averagePriceRecord.put("data", groupByAndCalculateAverage(allRecords, pivot.get(3), "average_price"));
                unitsSoldRecord.put("pivotId", pivot.get(3));
                unitsSoldRecord.put("data", groupByAndCalculateAverage(allRecords, pivot.get(3), "units"));
                totalSalesRecord.put("pivotId", pivot.get(3));
                totalSalesRecord.put("data", groupByAndCalculateAverage(allRecords, pivot.get(3), "total_sales"));
                demandRecord.put("averagePrice", averagePriceRecord);
                demandRecord.put("unitsSold", unitsSoldRecord);
                demandRecord.put("totalSales", totalSalesRecord);
                HashMap<String, Double> evAveragePriceRecords = groupByAndCalculateAverage(allRecords, "ev", "average_price");
                HashMap<String, Double> evTotalSalesRecords = groupByAndCalculateAverage(allRecords, "ev", "average_price");
                demandRecord.put("evPremium", (evAveragePriceRecords.get("\"EV\"") / evAveragePriceRecords.get("\"\"")) - 1);
                demandRecord.put("evMarketShare", evTotalSalesRecords.get("\"EV\"") / (evTotalSalesRecords.get("\"EV\"") + evTotalSalesRecords.get("\"\"")));
                dataFileWriter.append(demandRecord);
                break;
            default:
                throw new IOException("Envelope Schema contains unsupported data type: " + envelopeSchema.getName());
        }


        dataFileWriter.close();
        return outputFile;
    }

    public class Application {
        private String pivotId;
        ArrayList<Object> data = new ArrayList<Object>();


        // Getter Methods
        public String getPivotId() {
            return pivotId;
        }

        // Setter Methods
        public void setPivotId(String pivotId) {
            this.pivotId = pivotId;
        }
    }

    private float getRandomNumber(int minLimit, int maxLimit, double decimalPlace) {
        return (float) (Math.round(((minLimit + random.nextFloat() * (maxLimit - minLimit)) * decimalPlace)) / decimalPlace);
    }

    public HashMap<String, Double> groupByAndCalculateAverage(ArrayList<GenericRecord> allRecords, String groupByField, String field) {
        HashMap<String, Double> data = new HashMap<>();
        allRecords.stream()
                .collect(Collectors.groupingBy(
                        genericRecord -> genericRecord.get(groupByField),
                        Collectors.summarizingDouble(genericRecord -> Objects.equals(field, "units") ? (int) genericRecord.get(field) : (float) genericRecord.get(field))
                )).forEach((k, v) -> {
                    data.put(k.toString(), Objects.equals(field, "average_price") ? v.getSum() / v.getCount() : v.getSum());
                });

        return data;
    }

    public int groupByAndCalculateCount(ArrayList<GenericRecord> allRecords, String groupByField) {
        Map<Object, Long> counted = allRecords.stream()
                .collect(Collectors.groupingBy(
                        genericRecord -> genericRecord.get(groupByField),
                        Collectors.counting()
                ));

        return counted.size();
    }

    public int groupByModelCountryAndCalculateCount(ArrayList<GenericRecord> allRecords, String model, String country) {
        Map<Object, Map<Object, Long>> groupedRecords = allRecords.stream()
                .collect(Collectors.groupingBy(
                        genericRecord -> genericRecord.get(model),
                        Collectors.groupingBy(
                                genericRecord -> genericRecord.get(country),
                                Collectors.counting())
                ));

        int count = 0;
        for (Map.Entry<Object, Map<Object, Long>> entry : groupedRecords.entrySet()) {
            Object key = entry.getKey();
            Map<Object, Long> value = entry.getValue();
            count += value.size();
        }

        return count;
    }

    public int groupByDateAndCalculateCount(ArrayList<GenericRecord> allRecords, String date) {
        Map<Object, Long> groupByDateRecords = allRecords.stream()
                .collect(Collectors.groupingBy(
                        genericRecord -> genericRecord.get(date),
                        Collectors.counting()
                ));

        LocalDate currentDate = LocalDate.now().minusMonths(3);
        long total = 0;
        for (Map.Entry<Object, Long> entry : groupByDateRecords.entrySet()) {
            Object k = entry.getKey();
            Long v = entry.getValue();
            if (LocalDate.parse(k.toString().substring(1, k.toString().length() - 1)).isAfter(currentDate)) {
                total += v;
            }
        }

        return (int) total;
    }
}