package client;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class HTTPClient {


    public static void main(String[] args) {
        HTTPClient httpClient = new HTTPClient();
        for (int i = 0; i < 1000; i++) {
            httpClient.sendPost("http://54.82.220.45:8080/data/");

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }


    public void sendPost(String urlString) {
        try {

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            String input = "{\"location\":1, \"data\":[{\"type\":1, \"value\":  7.94},{\"type\":2, \"value\":219.00},{\"type\":3, \"value\":512.00},{\"type\":4, \"value\":  2.50},"
                    + "{\"type\":5, \"value\":  100.00},{\"type\":6, \"value\":426.00},{\"type\":7, \"value\":570.00}]}";


            OutputStream os = conn.getOutputStream();
            os.write(input.getBytes());
            os.flush();

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                System.out.println("Good Response");
            }

            BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

            String output;
            System.out.println("Output from Server .... \n");
            while ((output = br.readLine()) != null) {
                System.out.println(output);
            }

            conn.disconnect();

        } catch (MalformedURLException e) {

            e.printStackTrace();

        } catch (IOException e) {

            e.printStackTrace();

        }

    }

}

