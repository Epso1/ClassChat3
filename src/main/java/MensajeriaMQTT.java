import org.eclipse.paho.client.mqttv3.*;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class MensajeriaMQTT {

    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String CLIENT_ID = "cesar";
    private static final String CHAT_TODOS_TOPIC = "/chat/todos";
    private static final String CHAT_INDIVIDUAL_TOPIC_FORMAT = "/chat/%s/%s";

    private MqttClient mqttClient;
    private Map<String, FileWriter> chatFiles;

    public MensajeriaMQTT() {
        chatFiles = new HashMap<>();
        try {
            mqttClient = new MqttClient(BROKER_URL, CLIENT_ID);
            connectToBroker();
            subscribeToTopics();
            startConsoleListener();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void connectToBroker() throws MqttException {
        mqttClient.connect();
        System.out.println("Conectado al broker MQTT");
    }

    private void subscribeToTopics() throws MqttException {
        subscribeToTopic(CHAT_TODOS_TOPIC);
    }

    private void subscribeToIndividualChatTopics(String student1, String student2) throws MqttException {
        String topic1To2 = String.format(CHAT_INDIVIDUAL_TOPIC_FORMAT, student1, student2);
        String topic2To1 = String.format(CHAT_INDIVIDUAL_TOPIC_FORMAT, student2, student1);
        subscribeToTopic(topic1To2);
        subscribeToTopic(topic2To1);
    }

    private void subscribeToTopic(String topic) throws MqttException {
        mqttClient.subscribe(topic, new IMqttMessageListener() {
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                handleIncomingMessage(topic, message.toString());
            }
        });
    }

    private void handleIncomingMessage(String topic, String message) {
        System.out.println("Nuevo mensaje en el topic '" + topic + "': " + message);
        saveMessageToChat(topic, message);
    }

    private void saveMessageToChat(String topic, String message) {
        try {
            FileWriter chatFile = getChatFile(topic);
            chatFile.write(message + "\n");
            chatFile.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private FileWriter getChatFile(String topic) throws IOException {
        if (!chatFiles.containsKey(topic)) {
            FileWriter chatFile = new FileWriter(topic.replace("/", "_") + ".txt", true);
            chatFiles.put(topic, chatFile);
        }
        return chatFiles.get(topic);
    }

    private void startConsoleListener() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("Ingrese un comando: ");
            String input = scanner.nextLine();
            handleConsoleCommand(input);
        }
    }

    private void handleConsoleCommand(String command) {
        String[] parts = command.split(" ");
        if (parts.length < 2) {
            System.out.println("Comando no válido.");
            return;
        }

        String action = parts[0];
        String target = parts[1];
        String message = "";
        if(parts.length == 2){
            message = "";
        }else {
            message = command.substring(action.length() + target.length()+2);
        }


        try {
            if ("send".equalsIgnoreCase(action)) {
                if ("todos".equalsIgnoreCase(target)) {
                    publishMessage(CHAT_TODOS_TOPIC, message);
                } else {
                    String[] students = target.split("/");
                    if (students.length == 2) {
                        subscribeToIndividualChatTopics(students[0], students[1]);
                        publishMessage(String.format(CHAT_INDIVIDUAL_TOPIC_FORMAT, students[0], students[1]), message);
                    } else {
                        System.out.println("Comando no válido.");
                    }
                }
            } else if ("chat".equalsIgnoreCase(action)) {
                String chatTopic;
                if ("todos".equalsIgnoreCase(target)) {
                    chatTopic = CHAT_TODOS_TOPIC;
                } else {
                    chatTopic = "/chat/" + target;
                }
                displayChat(chatTopic);
            } else {
                System.out.println("Comando no válido.");
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
    private void publishMessage(String topic, String message) throws MqttException {
        if (topic.equals("/chat/" + CLIENT_ID) || topic.startsWith("/chat/" + CLIENT_ID + "/")) {
            String messageWithClientId = CLIENT_ID + ": " + message;
            MqttMessage mqttMessage = new MqttMessage(messageWithClientId.getBytes());
            mqttClient.publish(topic, mqttMessage);
            System.out.println("Mensaje enviado al topic '" + topic + "': " + messageWithClientId);
        } else {
            System.out.println("No estás autorizado para enviar mensajes a este topic.");
        }
    }



    private void displayChat(String topic) {
        // Comprobar si existe el archivo
        if (!chatFiles.containsKey(topic)) {
            System.out.println("No existe el chat con el topic '" + topic + "'");
        }else{
            try {
                FileReader reader = new FileReader(topic.replace("/", "_") + ".txt");
                Scanner fileScanner = new Scanner(reader);

                System.out.println("Chat con el topic '" + topic + "':");
                System.out.println("====================================");
                while (fileScanner.hasNextLine()) {
                    System.out.println(fileScanner.nextLine());
                }
                System.out.println("====================================");
            } catch (IOException e) {
                e.printStackTrace();
            }

        }


    }

    public static void main(String[] args) {
        new MensajeriaMQTT();
    }
}
