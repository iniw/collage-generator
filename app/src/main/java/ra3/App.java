package ra3;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/// A collage generator that uses the Last.FM API to fetch a user's top albums.
/// For more info about the API, please read:
/// https://www.last.fm/api/intro
/// https://www.last.fm/api/show/user.getTopAlbums
public class App {
    public static void main(String[] args) {
        var frame = new JFrame(TITLE);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(INITIAL_SIZE);

        // The panel for user input fields, placed at the top
        var optionsPanel = new JPanel();
        var optionsLayout = new GroupLayout(optionsPanel);

        var usernameTextField = new JTextField(DEFAULT_USERNAME);
        usernameTextField.setColumns(10);

        var periodComboBox = new JComboBox<>(new Vector<>(PERIOD_MAPPING.keySet()));
        var dimensionComboBox = new JComboBox<>(new Vector<>(DIMENSION_MAPPING.keySet()));
        var collageSizeComboBox = new JComboBox<>(new Vector<>(COLLAGE_SIZE_MAPPING.keySet()));

        // The panel for the resulting collage, placed in the middle
        var collagePanel = new JPanel();

        // The button for generating the collage, placed at the bottom
        var generateButton = new JButton("Gerar collage");

        // Retrieve the properties from disk and update the UI
        var properties = new Properties();
        try (var inProperties = new FileInputStream(PROPERTIES_FILE_PATH)) {
            properties.load(inProperties);
        } catch (FileNotFoundException e) {
            System.out.println("Arquivo de propriedades não encontrado - usando valores padrão");
        } catch (Exception e) {
            System.err.println("Falha ao carregar as propriedades");
        }

        usernameTextField.setText(properties.getProperty(USERNAME_KEY, DEFAULT_USERNAME));
        periodComboBox.setSelectedItem(properties.getProperty(PERIOD_KEY, DEFAULT_PERIOD));
        dimensionComboBox.setSelectedItem(properties.getProperty(DIMENSION_KEY, DEFAULT_DIMENSION));
        collageSizeComboBox.setSelectedItem(properties.getProperty(COLLAGE_SIZE_KEY, DEFAULT_COLLAGE_SIZE));

        generateButton.addActionListener(e -> {
            try {
                final var API_URL = new URL("http://ws.audioscrobbler.com/2.0");

                // Establish HTTP connection to perform the API request
                var connection = (HttpURLConnection) API_URL.openConnection();
                connection.setRequestMethod("GET");
                connection.setDoOutput(true);

                // Extract user input and map it to the API parameters
                var period = PERIOD_MAPPING.get((String) periodComboBox.getSelectedItem());
                var limit = DIMENSION_MAPPING.get((String) dimensionComboBox.getSelectedItem());
                var collageSize = COLLAGE_SIZE_MAPPING.get((String) collageSizeComboBox.getSelectedItem());

                // Build params string and append it to the request
                var params = buildUrlParameterString(Map.of(
                        "api_key", API_KEY,
                        "method", "user.getTopAlbums",
                        "user", usernameTextField.getText(),
                        "period", period,
                        "limit", limit));

                var outStream = new DataOutputStream(connection.getOutputStream());
                outStream.writeBytes(params);

                // Perform request and check the response status
                var status = connection.getResponseCode();
                switch (status) {
                    // All went well
                    case 200:
                        break;
                    // URL not found - means the user doesn't exist
                    case 404:
                        throw new InvalidUser(usernameTextField.getText());
                    default:
                        throw new Exception(String.format("Erro no request (%d)", status));
                }

                // Parse the XML response
                var document = DocumentBuilderFactory
                        .newInstance()
                        .newDocumentBuilder()
                        .parse(connection.getInputStream());

                // Close the connection already since the ouput has been parsed
                connection.disconnect();

                // Extract image URLs and encode them into `BufferedImage`s
                var albumNodes = document.getElementsByTagName("album");
                if (albumNodes.getLength() == 0)
                    throw new NoRecentPlays(usernameTextField.getText());

                var images = new ArrayList<BufferedImage>();
                for (int i = 0; i < albumNodes.getLength(); i++) {
                    var albumNode = (Element) albumNodes.item(i);

                    // Find the image node with the desired size
                    var imageNodes = albumNode.getElementsByTagName("image");
                    for (int j = 0; j < imageNodes.getLength(); j++) {
                        var imageNode = imageNodes.item(j);

                        if (imageNode.getAttributes().getNamedItem("size").getNodeValue().equals(collageSize)) {
                            var imageUrl = imageNode.getTextContent();
                            // Last.FM may not have an image for the requested size/album, just skip it
                            if (imageUrl.isEmpty())
                                break;

                            images.add(ImageIO.read(new URL(imageUrl)));
                            break;
                        }
                    }
                }

                // Remove previously rendered collage (if any)
                collagePanel.removeAll();

                if (images.isEmpty())
                    throw new NoImagesAvailable(usernameTextField.getText());

                var imageWidth = images.get(0).getWidth();
                var imageHeight = images.get(0).getHeight();

                // Render images to a single texture on a 2d grid
                var collageScale = (int) Math.sqrt(Integer.valueOf(limit));
                var collageWidth = imageWidth * collageScale;
                var collageHeight = imageHeight * collageScale;

                var collage = new BufferedImage(collageWidth, collageHeight, BufferedImage.TYPE_INT_RGB);
                var graphics = collage.createGraphics();

                for (int i = 0; i < images.size(); i++) {
                    var x = (i % collageScale) * imageWidth;
                    var y = (i / collageScale) * imageHeight;
                    graphics.drawImage(images.get(i), x, y, null);
                }

                graphics.dispose();

                // Insert the rendered collage
                collagePanel.add(new JLabel(new ImageIcon(collage)));

                // Adjust the window size to fit the collage and re-center it
                frame.pack();
                frame.setLocationRelativeTo(null);

                // Retrieve the properties from the UI and save them to disk
                properties.setProperty(USERNAME_KEY, usernameTextField.getText());
                properties.setProperty(PERIOD_KEY, (String) periodComboBox.getSelectedItem());
                properties.setProperty(DIMENSION_KEY, (String) dimensionComboBox.getSelectedItem());
                properties.setProperty(COLLAGE_SIZE_KEY, (String) collageSizeComboBox.getSelectedItem());

                try (var outProperties = new FileOutputStream(PROPERTIES_FILE_PATH)) {
                    properties.store(outProperties, null);
                } catch (Exception ex) {
                    System.err.println("Falha ao salvar as propriedades");
                }

            } catch (Exception exception) {
                JOptionPane.showMessageDialog(
                        frame,
                        String.format("[%s] - %s", exception.getClass().getName(), exception.getMessage()),
                        "Falha ao gerar collage",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        // Layout the option panel horizontally
        optionsLayout.setHorizontalGroup(
                optionsLayout.createSequentialGroup()
                        .addComponent(usernameTextField)
                        .addComponent(periodComboBox)
                        .addComponent(dimensionComboBox)
                        .addComponent(collageSizeComboBox));

        // Layout the main frame vertically
        frame.getContentPane().add(optionsPanel, BorderLayout.NORTH);
        frame.getContentPane().add(collagePanel, BorderLayout.CENTER);
        frame.getContentPane().add(generateButton, BorderLayout.SOUTH);

        // Center the window on the screen and render it
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static LinkedHashMap<String, String> buildPeriodMapping() {
        var mapping = new LinkedHashMap<String, String>();
        mapping.put("Semana", "7day");
        mapping.put("1 Mês", "1month");
        mapping.put("3 meses", "3month");
        mapping.put("6 meses", "6month");
        mapping.put("1 Ano", "12month");
        return mapping;
    }

    private static LinkedHashMap<String, String> buildDimensionMapping() {
        var mapping = new LinkedHashMap<String, String>();
        mapping.put("3x3", "9");
        mapping.put("5x5", "25");
        mapping.put("10x10", "100");
        return mapping;
    }

    private static LinkedHashMap<String, String> buildCollageSizeMapping() {
        var mapping = new LinkedHashMap<String, String>();
        mapping.put("Pequena", "small");
        mapping.put("Média", "medium");
        mapping.put("Grande", "large");
        mapping.put("Extra grande", "extralarge");
        return mapping;
    }

    private static String buildUrlParameterString(Map<String, String> params) throws UnsupportedEncodingException {
        var result = new StringBuilder();
        for (var entry : params.entrySet()) {
            result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            result.append("&");
        }

        return result.length() > 0 ? result.substring(0, result.length() - 1) : result.toString();
    }

    /// UI constants
    public static final String TITLE = "Gerador de collage Last.FM";
    public static final Dimension INITIAL_SIZE = new Dimension(550, 350);

    // Maps the user-friendly period names to the API parameter `period`.
    // LinkedHashMap is used to preserve insertion order.
    public static final LinkedHashMap<String, String> PERIOD_MAPPING = buildPeriodMapping();

    // Maps the user-friendly dimension names to the API parameter `limit`.
    // LinkedHashMap is used to preserve insertion order.
    public static final LinkedHashMap<String, String> DIMENSION_MAPPING = buildDimensionMapping();

    // Maps the user-friendly collage size names to the available image sizes
    // returned from the API.
    // LinkedHashMap is used to preserve insertion order.
    public static final LinkedHashMap<String, String> COLLAGE_SIZE_MAPPING = buildCollageSizeMapping();

    // The default values for the user input fields
    public static final String DEFAULT_USERNAME = "Usuário";
    public static final String DEFAULT_PERIOD = DIMENSION_MAPPING.keySet().toArray()[0].toString();
    public static final String DEFAULT_DIMENSION = PERIOD_MAPPING.keySet().toArray()[0].toString();
    public static final String DEFAULT_COLLAGE_SIZE = COLLAGE_SIZE_MAPPING.keySet().toArray()[0].toString();

    /// Properties constants
    public static final String PROPERTIES_FILE_PATH = "options.txt";
    public static final String USERNAME_KEY = "username";
    public static final String PERIOD_KEY = "period";
    public static final String DIMENSION_KEY = "dimension";
    public static final String COLLAGE_SIZE_KEY = "image-size";

    /// API constants
    public static final String API_KEY = "183cf3f543bbc099bf108c6f8560bdcd";

}
