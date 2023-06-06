import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
public class FolderCreatorAndResourceDownloader {
    private static List<DownloadedResource> downloadedResources = new ArrayList<>();
    private static LocalDateTime downloadStartTime;
    private static LocalDateTime downloadEndTime;

    public static void main(String[] args) throws URISyntaxException {
        String urlstr = "https://facebook.com";
        System.out.println(urlstr);
        try {
            // Validate the URL
            URI validUrl = new URL(urlstr).toURI();

            System.out.println("validUrl ..... =: "+validUrl);
            // Create a folder using the host name of the URL
            String host = validUrl.getHost();
            String folderName = host.replaceAll("[^a-zA-Z0-9.-]", "_"); // Replace invalid characters with underscores

            File folder = new File(folderName);
            if (folder.mkdir()) {
                System.out.println("Folder created successfully");
            } else {
                System.out.println("Failed to create folder");
            }

            // Extract the filename from the URL
            String fileName = Paths.get(new URI(urlstr).getPath()).getFileName().toString();
            if (fileName.isEmpty() || fileName.endsWith("/")) {
                fileName = "index.html";
            } else {
                int lastSlashIndex = fileName.lastIndexOf("/");
                if (lastSlashIndex != -1) {
                    fileName = fileName.substring(lastSlashIndex + 1);
                }
            }

            // Download the home page and store it with its filename

            File outputFile = new File(folderName, fileName);
            URLConnection connection = validUrl.toURL().openConnection();

            try (InputStream inputStream = connection.getInputStream()) {
                Files.copy(inputStream, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Files downloaded successfully: " + outputFile.getAbsolutePath());
            } catch (IOException e) {
                System.out.println("Failed to download the file.");
                e.printStackTrace();
            }

            //extract html contents

            String htmlContents = readFileAsString(outputFile);
            Document doc = Jsoup.parse(htmlContents, urlstr);
            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String extractedLink = link.attr("abs:href");

                // Store the extracted links in the DBMS
//            insertLinkToDB(extractedLink);

                //update the console/GUi
                System.out.println("Extracted link: " + extractedLink);

                // Download the corresponding resource/file
                if(extractedLink.endsWith("/")){
                    System.out.println("I got stacked @here");
                    String extractedLink1 = extractedLink.substring(0,extractedLink.lastIndexOf('/'));
                    downloadResource(extractedLink1, folder);
                }else{
                 downloadResource(extractedLink, folder);
                }
                //Generate
            }

        } catch (IOException e) {
            System.out.println("Invalid URL: " + urlstr);
            e.printStackTrace();
        }
    }

    private static String readFileAsString(File file) throws IOException {
        StringBuilder contentBuilder = new StringBuilder();
        try  {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                contentBuilder.append(line).append("\n");
            }
//            reader.close();
        }catch (IOException e){
//            System.out.println(e.printStackTrace());
            e.printStackTrace();
        }

        return contentBuilder.toString();
    }

    // Helper method to insert extracted links into the DBMS

    private static void insertLinkToDB(String link) {
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/dbname", "username", "password")) {
            PreparedStatement stmt = conn.prepareStatement("Insert into links (url) values (?)");
        } catch (SQLException e) {
            System.out.println("Failed to insert link into the DBMS.");
            e.printStackTrace();
        }
    }

    // Helper method to downloadResource
    private static void downloadResource(String urlstr, File folder) {
        try {
            URL url = new URL(urlstr);
            URLConnection connection = url.openConnection();
            int contentLength = connection.getContentLength();

            String fileName = Paths.get(new URI(urlstr).getPath()).getFileName().toString();

            if (fileName.isEmpty() || fileName.endsWith("/")) {
                fileName = "index.html";
            } else {
                int lastSlashIndex = fileName.lastIndexOf('/');
                if (lastSlashIndex != -1) {
                    fileName = fileName.substring(lastSlashIndex + 1);
                }

            }

            File outputfile = new File(folder, fileName);

            try (InputStream inputStream = connection.getInputStream()) {
                Files.copy(inputStream, outputfile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Resource downloaded successfully " + outputfile.getAbsolutePath());

                // Calculate the download time
                LocalDateTime startTime = LocalDateTime.now();
                long startMills = System.currentTimeMillis();

                // Store the downloaded resource details
                DownloadedResource downloadedResource = new DownloadedResource(urlstr, contentLength, startTime, startMills);
                downloadedResources.add(downloadedResource);

                //update the console with the download progress report
                long endMills = System.currentTimeMillis();
                long downloadTime = endMills - startMills;
                System.out.println("Downloaded: " + urlstr);
                System.out.println("Size: " + contentLength + " bytes");
                System.out.println("Download Time: " + downloadTime + " ms");
                System.out.println("-----------------------------------------");
                System.out.println("Resources download details");
                System.out.println("-----------------------------------------");
                for (DownloadedResource resource : downloadedResources) {
                    System.out.println("URl: " + resource.getUrl());
                    System.out.println("Size: " + resource.getSize());
                    System.out.println("Download Time: " + resource.getDownloadTime() + " ms");
                    System.out.println("-----------------------------------------");
                }
            } catch (IOException e) {
                System.out.println("Failed to download the resource");
                throw new RuntimeException(e);
            }
        } catch (MalformedURLException e) {
            System.out.println("Invalid url");
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    //helper class to represent a downloaded resource
    private static class DownloadedResource {
        private String url;
        private int size;
        private LocalDateTime startTime;
        private long startMillis;

        public DownloadedResource(String url, int size, LocalDateTime startTime, long startMillis) {
            this.url = url;
            this.size = size;
            this.startTime = startTime;
            this.startMillis = startMillis;
        }

        public String getUrl() {
            return url;
        }

        public int getSize() {
            return size;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public long getStartMillis() {
            return startMillis;
        }

        public long getDownloadTime() {
            long endMillis = System.currentTimeMillis();
            return endMillis - startMillis;
        }
    }
}