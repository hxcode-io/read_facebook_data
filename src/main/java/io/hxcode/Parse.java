package io.hxcode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public class Parse {

    protected static final List<String> ids = new ArrayList<>();

    public static void main(String[] args) {

        // create a reader
        var outputPath = Paths.get("out");
        try {
            Files.createDirectories(outputPath);
            deleteDirectoryStream(outputPath);

            var imagePath = Paths.get("src", "main", "resources");
            var path = Paths.get("src", "main", "resources", "posts");

            Files.walk(path, 1).filter(path1 -> path1.toString().endsWith(".json")).forEach(jsonPath -> {
                System.out.printf("JSON found: %s%n", jsonPath.toAbsolutePath());

                try {
                    Reader reader = Files.newBufferedReader(jsonPath);

                    //create ObjectMapper instance
                    ObjectMapper objectMapper = new ObjectMapper();

                    //read customer.json file into tree model
                    JsonNode parser = objectMapper.readTree(reader);

                    // read projects
                    for (Iterator<JsonNode> it = parser.elements(); it.hasNext(); ) {
                        writeInterview(outputPath, imagePath, it.next());
                    }

                    //close reader
                    reader.close();

                    System.out.printf("Count IDs: %d", ids.size());

                    BufferedWriter writer = Files.newBufferedWriter(outputPath.resolve("ids.json"));
                    ObjectMapper mapper = new ObjectMapper();
                    writer.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ids));
                    writer.close();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }

    public static void writeInterview(Path outputPath, Path imagePath, JsonNode interview) throws IOException {
        var id = interview.path("timestamp").asText();
//        System.out.printf("execute %s%n", id);
        var date = new Date(interview.path("timestamp").asLong() * 1000);

        var post = interview.at("/data/0/post").asText("");
        if (post.isBlank()) {
            System.out.printf("No data with text found %s%n", id);
            return;
        }
        var interviewText = getText(post);
        if (interviewText == null) return;

        var imageFile = getImageFile(id, imagePath, interview);
        if (imageFile == null) {
            System.out.printf("No image file found %s%n", id);
            return;
        }

        System.out.printf("%s --- %tF --- %.40s --- %.40s --- %.1fkB %n",
                id,
                date,
                interviewText.de,
                interviewText.en,
                (double)imageFile.length() / 1000
        );

        var localDT = convertToLocalDateTime(date);
        var interviewPath = outputPath.resolve(Paths.get(
                String.format("%d",localDT.getYear()),
                String.format("%02d",localDT.getMonthValue()),
                String.format("%02d",localDT.getDayOfMonth())
        ));
        Files.createDirectories(interviewPath);

        var destImagePath = interviewPath.resolve(String.format("%s-image.jpg", id));
        Files.copy(imageFile.toPath(), destImagePath);

        BufferedWriter writer = Files.newBufferedWriter(interviewPath.resolve(String.format("%s-interview.json", id)));
        Map<String, Object> customer = new HashMap<>();
        customer.put("id", id);
        customer.put("en", interviewText.en);
        customer.put("de", interviewText.de);

        ObjectMapper mapper = new ObjectMapper();
        writer.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(customer));
        writer.close();

        ids.add(id);
    }

    public static LocalDateTime convertToLocalDateTime(Date dateToConvert) {
        return dateToConvert.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    public static File getImageFile(String id, Path imagePath, JsonNode interview) {
        var imageUri = interview.at("/attachments/0/data/0/media/uri").asText();
        if (imageUri == null || imageUri.equals("")) {
            System.out.printf("No image URI found %s%n", id);
            return null;
        }
        var file = new File(imagePath.toFile(), imageUri);
        if (!file.exists()) {
            System.out.printf("No image file found: %s %n", file.getAbsolutePath());
            return null;
        }
        return file;
    }

    public static InterviewText getText(String post) {
        var decodedPost = decodeText(post);
        var splits = decodedPost.split("\n--\n");
        if (splits.length != 2) {
            splits = decodedPost.split("\n-\n");
        }
        if (splits.length != 2) {
            splits = decodedPost.split("\n\nhttps://www.toyota-crowd.de/powerlionacacio \n\n");
        }
        if (splits.length != 2) {
            splits = decodedPost.split("\"\n\n\"");
            if (splits.length == 2) {
                splits[0] = splits[0] + "\"";
                splits[1] = "\"" + splits[1];
            }
        }
        if (splits.length != 2) {
            return null;
        }
        return new InterviewText(splits[0], splits[1]);
    }

    public static String decodeText(String text) {
        var textDecode = text.replaceAll("\u00c3\u00a4", "ä");
        textDecode = textDecode.replaceAll("\u00c3\u0084", "Ä");
        textDecode = textDecode.replaceAll("\u00c3\u009c", "Ü");
        textDecode = textDecode.replaceAll("\u00c3\u00bc", "ü");
        textDecode = textDecode.replaceAll("\u00c3\u00b6", "ö");
        textDecode = textDecode.replaceAll("\u00c3\u0096", "Ö");
        textDecode = textDecode.replaceAll("\u00e2\u0080\u0099", "'");
        textDecode = textDecode.replaceAll("\u00e2\u0080\u0093", "–");
        textDecode = textDecode.replaceAll("\u00c3\u009f", "ß");
        textDecode = textDecode.replaceAll("\u00c3\u00a9", "é");
        textDecode = textDecode.replaceAll("\u00e2\u0080\u009d", "\"");
        textDecode = textDecode.replaceAll("\u00e2\u0080\u009c", "\"");
        textDecode = textDecode.replaceAll("\u00e2\u0080\u009e", "\"");
        return textDecode;
    }

    public record InterviewText (String de, String en) { }

    public static void deleteDirectoryStream(Path path) throws IOException {
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
}
