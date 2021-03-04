package net.fununity.cloud.server.misc;

import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.server.Server;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Singelton class for handling the cloud configuration.
 * Used to store/retrieve default servers.
 * @since 0.0.1
 * @author Marco Hajek
 */
public class ConfigHandler {

    private static ConfigHandler instance;

    /**
     * Gets the instance of the singelton.
     * @return ConfigHandler - the handler.
     * @since 0.0.1
     */
    public static ConfigHandler getInstance(){
        if(instance == null)
            instance = new ConfigHandler();
        return instance;
    }

    private File configFile;
    private final Logger logger = Logger.getLogger(ConfigHandler.class);

    private ConfigHandler() {
        logger.addAppender(new ConsoleAppender(new PatternLayout("[%d{HH:mm:ss}] %c{1} [%p]: %m%n")));
        logger.setAdditivity(false);
        logger.setLevel(Level.INFO);
        try {
            this.configFile = new File("config.xml");
            if(!this.configFile.exists()) {
                boolean newFile = this.configFile.createNewFile();
                if(newFile)
                    this.loadDefaultConfiguration();
            }
            this.loadDefaultServers();
        } catch(IOException e) {
            logger.warn(e.getMessage());
        }
    }

    /**
     * Saves the default configuration to the file.
     * @since 0.0.1
     */
    private void loadDefaultConfiguration(){
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.newDocument();

            Element rootElem = doc.createElement("cloudserver");
            doc.appendChild(rootElem);

            Element defaultServers = doc.createElement("defaultservers");
            rootElem.appendChild(defaultServers);

            defaultServers.appendChild(this.createServer(doc, "Main", "127.0.0.1", "25565", "512M" ,"BungeeCord-Main", "BungeeCord"));
            defaultServers.appendChild(this.createServer(doc, "Lobby01", "127.0.0.1", "25566", "512M", "Lobby01", "Lobby"));
            defaultServers.appendChild(this.createServer(doc, "Lobby02", "127.0.0.1", "25567", "512M", "Lobby02", "Lobby"));

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(this.configFile);
            transformer.transform(source, result);
        } catch (ParserConfigurationException | TransformerException e) {
            logger.warn(e.getMessage());
        }
    }

    /**
     * Creates a server element for the config.
     * @param doc Document - the xml document.
     * @param id String - the server id.
     * @param ip String - the server ip.
     * @param port String - the server port.
     * @param maxRam String - the max ram configuration of the server.
     * @param motd String - the motd of the server.
     * @param type String - the type of the server.
     * @return Element - the xml version of this server.
     * @since 0.0.1
     */
    private Element createServer(Document doc, String id, String ip, String port, String maxRam, String motd, String type){
        Element server = doc.createElement("server");
        Element elementId = doc.createElement("id");
        Element elementIp =  doc.createElement("ip");
        Element elementPort = doc.createElement("port");
        Element elementRam = doc.createElement("maxRam");
        Element elementMotd = doc.createElement("motd");
        Element elementType = doc.createElement("type");

        elementId.setTextContent(id);
        elementIp.setTextContent(ip);
        elementPort.setTextContent(port);
        elementRam.setTextContent(maxRam);
        elementMotd.setTextContent(motd);
        elementType.setTextContent(type);

        server.appendChild(elementId);
        server.appendChild(elementIp);
        server.appendChild(elementPort);
        server.appendChild(elementRam);
        server.appendChild(elementMotd);
        server.appendChild(elementType);

        return server;
    }

    /**
     * Loads the default servers from the config and adds the to the cloud.
     * @since 0.0.1
     */
    public void loadDefaultServers() {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DocumentBuilder docBuilder = dbFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(this.configFile);

            Element root = doc.getDocumentElement();
            NodeList defaultServers = root.getElementsByTagName("defaultservers").item(0).getChildNodes();
            List<Server> serverToStart = new ArrayList<>();
            for(int i = 0; i < defaultServers.getLength(); i++){
                Node server = defaultServers.item(i);
                NodeList children = server.getChildNodes();
                String id = children.item(0).getTextContent();
                String ip = children.item(1).getTextContent();
                int port = Integer.parseInt(children.item(2).getTextContent());
                String maxRam = children.item(3).getTextContent();
                String motd = children.item(4).getTextContent();
                int maxPlayers =  Integer.parseInt(children.item(5).getTextContent());
                String type = children.item(6).getTextContent();
                ServerType serverType = Arrays.stream(ServerType.values()).filter(t-> t.name().equalsIgnoreCase(type)).findFirst().orElse(null);
                serverToStart.add(new Server(id, ip, port, maxRam, motd, maxPlayers, serverType));
            }
            // Niko change: moved under for loop, because when other servers are created
            // the starting server won't send the cloud event properly
            serverToStart.forEach(s -> ServerHandler.getInstance().addServer(s));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            logger.warn(e.getMessage());
        }
    }
}
