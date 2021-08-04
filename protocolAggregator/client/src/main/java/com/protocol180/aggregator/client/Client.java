package com.protocol180.aggregator.client;

import com.protocol180.aggregator.commons.MockClientUtil;
import com.protocol180.aggregator.commons.Utility;
import com.r3.conclave.common.EnclaveInstanceInfo;
import com.r3.conclave.mail.Curve25519PrivateKey;
import com.r3.conclave.mail.EnclaveMail;
import com.r3.conclave.mail.PostOffice;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.util.*;

public class Client {

    // Create list of identities used in network
    static Map<Curve25519PrivateKey, String> randomClients = new HashMap<>();
    static Curve25519PrivateKey provider1 = Curve25519PrivateKey.random();
    static Curve25519PrivateKey provider2 = Curve25519PrivateKey.random();
    static Curve25519PrivateKey consumer = Curve25519PrivateKey.random();
    static Curve25519PrivateKey provenance = Curve25519PrivateKey.random();

    File dataInputFileForAggregation;
    File envelopeFile;
    File identitiesFile;


    public Client() throws IOException {
        initClient();
        randomClients.put(provider1, Utility.CLIENT_PROVIDER);
        randomClients.put(provider2, Utility.CLIENT_PROVIDER);
        randomClients.put(consumer, Utility.CLIENT_CONSUMER);
        randomClients.put(provenance, Utility.CLIENT_PROVENANCE);
    }

    public void initClient() throws IOException {
        envelopeFile = new File(ClassLoader.getSystemClassLoader().getResource("envelope.avsc").getPath());
        Schema envelopeSchema = new Schema.Parser().parse(envelopeFile);
        Schema aggregationInputSchema = envelopeSchema.getField("aggregateInput").schema();
        Schema aggregationOutputSchema = envelopeSchema.getField("aggregateOutput").schema();
        Schema provenanceOutputSchema = envelopeSchema.getField("provenanceOutput").schema();
        Schema identitySchema = envelopeSchema.getField("identity").schema();

        //create generic records using avro schema for aggregation input to the enclave and append to file
        ArrayList<GenericRecord> records = MockClientUtil.createGenericSchemaRecords(aggregationInputSchema);
        dataInputFileForAggregation = MockClientUtil.createAvroDataFileFromGenericRecords(aggregationInputSchema, records, "aggregateInput.avro");

        //create generic records using identities schema for identify client inside enclave and append to file
        ArrayList<GenericRecord> identitiesRecords = new ArrayList<>();
        for (Map.Entry<Curve25519PrivateKey, String> clientEntry : randomClients.entrySet()) {
            GenericRecord demandRecord = new GenericData.Record(identitySchema);
            demandRecord.put("publicKey", Base64.getEncoder().encodeToString(clientEntry.getKey().getPublicKey().getEncoded()));
            demandRecord.put("clientType", clientEntry.getValue());
            identitiesRecords.add(demandRecord);
        }

        identitiesFile = MockClientUtil.createAvroDataFileFromGenericRecords(identitySchema, identitiesRecords, "identities.avro");


    }


    public static void main(String[] args) {


        // Connect to the host, it will send us a remote attestation (EnclaveInstanceInfo).
        try {
            Client client = new Client();
            int clientPort = 9999;
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), clientPort), 5000);
            DataInputStream fromHost = new DataInputStream(socket.getInputStream());
            DataOutputStream toHost = new DataOutputStream(socket.getOutputStream());


            //Reading enclave attestation from host and validating it.
            byte[] attestationBytes = new byte[fromHost.readInt()];
            fromHost.readFully(attestationBytes);
            EnclaveInstanceInfo attestation = EnclaveInstanceInfo.deserialize(attestationBytes);
            // Check it's the enclave we expect. This will throw InvalidEnclaveException if not valid.
            System.out.println("Connected to " + attestation);
//        EnclaveConstraint.parse("S:360585776942A4E8A6BD70743E7C114A81F9E901BF90371D27D55A241C738AD9 "
//                + "S:4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4 PROD:1 SEC:INSECURE").check(attestation);


            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String clientMessage = "", serverMessage = "";
            int messageCounter = 1;
//            String typeOfMessage[]=[];
            while (messageCounter < 6) {
                System.out.println("Press Enter to send mail " + messageCounter + " to Host.");
                clientMessage = br.readLine();
                PostOffice postOffice = null;
                byte[] encryptedMail = client.getEncryptedMail(messageCounter, attestation, postOffice);
                toHost.writeInt(encryptedMail.length);
                toHost.write(encryptedMail);
                toHost.flush();
                if (messageCounter == 4 || messageCounter == 5) {
                    byte[] encryptedReply = new byte[fromHost.readInt()];
                    fromHost.readFully(encryptedReply);
                    System.out.println("Reading reply mail of length " + encryptedReply.length + " bytes.");
                    // The same post office will decrypt the response.
                    EnclaveMail reply = postOffice.decryptMail(encryptedReply);
                    System.out.println("Enclave reply: '" + new String(reply.getBodyAsBytes()) + "'");
                }
                messageCounter++;
            }
            toHost.close();
            fromHost.close();
            socket.close();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private byte[] getEncryptedMail(int messageCounter, EnclaveInstanceInfo attestation, PostOffice postOffice) throws IOException {
        byte[] encryptedMail;
        if (messageCounter == 1) {
            postOffice = attestation.createPostOffice(provider1, "aggregate");
            encryptedMail = postOffice.encryptMail(Files.readAllBytes(envelopeFile.toPath()));
        } else if (messageCounter == 2) {
            postOffice = attestation.createPostOffice(provider1, "aggregate");
            encryptedMail = postOffice.encryptMail(Files.readAllBytes(identitiesFile.toPath()));
        } else if (messageCounter == 3) {
            postOffice = attestation.createPostOffice(provider1, "aggregate");
            encryptedMail = postOffice.encryptMail(Files.readAllBytes(dataInputFileForAggregation.toPath()));
        } else if (messageCounter == 4) {
            postOffice = attestation.createPostOffice(consumer, "aggregate");
            encryptedMail = postOffice.encryptMail("test consumer".getBytes());
        } else {

            postOffice = attestation.createPostOffice(provenance, "aggregate");
            encryptedMail = postOffice.encryptMail("test provenance".getBytes());
        }

        return encryptedMail;


//        Thread.sleep(3000);
    }

}
