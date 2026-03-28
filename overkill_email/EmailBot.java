import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.List;

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

public class EmailBot {

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
        .setDataStoreFactory(
            new FileDataStoreFactory(new File("tokens"))
        )
        .setAccessType("offline")
        .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
            .setPort(8888)
            .build();

        Credential credential = new AuthorizationCodeInstalledApp(
            flow, receiver
        ).authorize("user");

        return new Gmail.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
        .setApplicationName("OverkillEmail")
        .build();
    }







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




    public static void main(String[] args) throws Exception {
        System.out.println("Starting OverkillEmail...");

            Gmail service = getGmailService();
            System.out.println("Successfully connected to Gmail!");

            printUnreadEmails(service);


    }

}   