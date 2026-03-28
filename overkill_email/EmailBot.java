import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.json.JSONObject;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.services.gmail.model.ModifyMessageRequest;

public class EmailBot {

    // -------------------------------------------------------------------------
    // AUTHENTICATION
    // -------------------------------------------------------------------------

    private static Gmail getGmailService() throws Exception {
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
            GsonFactory.getDefaultInstance(),
            new FileReader("credentials.json")
        );

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            clientSecrets,
            Arrays.asList(GmailScopes.GMAIL_MODIFY)
        )
        .setDataStoreFactory(new FileDataStoreFactory(new File("tokens")))
        .setAccessType("offline")
        .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
            .setPort(8888)
            .build();

        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver)
            .authorize("user");

        return new Gmail.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
        .setApplicationName("OverkillEmail")
        .build();
    }

    // -------------------------------------------------------------------------
    // EMAIL READING
    // -------------------------------------------------------------------------

    private static void printUnreadEmails(Gmail service) throws Exception {
        List<Message> messages = service.users().messages()
            .list("me")
            .setQ("is:unread")
            .execute()
            .getMessages();

        if (messages == null || messages.isEmpty()) {
            System.out.println("No unread emails found.");
            return;
        }

        System.out.println("Found " + messages.size() + " unread emails:");

        for (Message message : messages) {
            Message fullMessage = service.users().messages()
                .get("me", message.getId())
                .setFormat("full")
                .execute();

            List<MessagePartHeader> headers = fullMessage.getPayload().getHeaders();
            for (MessagePartHeader header : headers) {
                if (header.getName().equals("Subject")) {
                    System.out.println("Subject: " + header.getValue());
                }
                if (header.getName().equals("From")) {
                    System.out.println("From: " + header.getValue());
                }
            }
            System.out.println("---");
        }
    }

    private static String extractBody(Message message) throws Exception {
        // Simple emails have the body directly in the payload
        if (message.getPayload().getBody().getData() != null) {
            byte[] bodyBytes = Base64.getUrlDecoder()
                .decode(message.getPayload().getBody().getData());
            return new String(bodyBytes);
        }

        // Multipart emails have the body split into parts
        if (message.getPayload().getParts() != null) {
            for (var part : message.getPayload().getParts()) {
                if (part.getMimeType().equals("text/plain")
                        && part.getBody().getData() != null) {
                    byte[] bodyBytes = Base64.getUrlDecoder()
                        .decode(part.getBody().getData());
                    return new String(bodyBytes);
                }
            }
        }

        return "(no body found)";
    }

    // -------------------------------------------------------------------------
    // PYTHON / AI ANALYSIS
    // -------------------------------------------------------------------------

    private static String analyzeEmail(String emailContent) throws Exception {
        URL url = new URL("http://localhost:5000/analyze");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        // Clean the content so it doesn't break the JSON format
        String cleanedEmail = emailContent
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "");

        String jsonBody = "{\"email\": \"" + cleanedEmail + "\"}";
        conn.getOutputStream().write(jsonBody.getBytes());

        Scanner scanner = new Scanner(conn.getInputStream());
        String response = scanner.useDelimiter("\\A").next();
        scanner.close();

        return response;
    }

    // -------------------------------------------------------------------------
    // EMAIL SENDING
    // -------------------------------------------------------------------------

    private static void sendReply(Gmail service, String to,
                                   String subject, String body) throws Exception {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);
        MimeMessage email = new MimeMessage(session);

        email.setFrom(new InternetAddress("me"));
        email.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(to));
        email.setSubject("Re: " + subject);
        email.setText(body);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        email.writeTo(buffer);
        String encodedEmail = Base64.getUrlEncoder().encodeToString(buffer.toByteArray());

        Message message = new Message();
        message.setRaw(encodedEmail);
        service.users().messages().send("me", message).execute();

        System.out.println("Replied to: " + to);
    }

    private static void markAsRead(Gmail service, String messageId) throws Exception {
        ModifyMessageRequest request = new ModifyMessageRequest()
            .setRemoveLabelIds(Arrays.asList("UNREAD"));

        service.users().messages()
            .modify("me", messageId, request)
            .execute();
    }

    // -------------------------------------------------------------------------
    // MAIN LOOP
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        System.out.println("Starting OverkillEmail...");

        try {
            Gmail service = getGmailService();
            System.out.println("Successfully connected to Gmail!");

            String account = service.users().getProfile("me")
                .execute().getEmailAddress();
            System.out.println("Connected as: " + account);

            while (true) {
                System.out.println("Checking for unread emails...");

                List<Message> messages = service.users().messages()
                    .list("me")
                    .setQ("is:unread")
                    .execute()
                    .getMessages();

                if (messages == null || messages.isEmpty()) {
                    System.out.println("No unread emails.");
                } else {
                    System.out.println("Found " + messages.size() + " unread emails!");

                    for (Message msg : messages) {
                        // Fetch full email
                        Message full = service.users().messages()
                            .get("me", msg.getId())
                            .setFormat("full")
                            .execute();

                        // Extract subject and sender
                        String subject = "";
                        String from = "";
                        for (MessagePartHeader header : full.getPayload().getHeaders()) {
                            if (header.getName().equals("Subject"))
                                subject = header.getValue();
                            if (header.getName().equals("From"))
                                from = header.getValue();
                        }

                        // Extract body
                        String body = extractBody(full);

                        System.out.println("Processing email from: " + from);
                        System.out.println("Subject: " + subject);

                        // Send to Python for AI analysis
                        String analysis = analyzeEmail(body);

                        // Parse just the response field from the JSON
                        String overkillResponse = analysis;
                        try {
                            JSONObject outer = new JSONObject(analysis);
                            JSONObject inner = new JSONObject(outer.getString("result"));
                            overkillResponse = inner.getString("response");
                        } catch (Exception parseError) {
                            System.out.println("Could not parse response: " 
                                + parseError.getMessage());
                        }

                        // Send the overkill reply
                        sendReply(service, from, subject, overkillResponse);

                        // Mark as read so we don't reply again
                        markAsRead(service, msg.getId());
                    }
                }

                System.out.println("Waiting 10 seconds...");
                Thread.sleep(10000);
            }

        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
