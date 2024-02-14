package org.mybots;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class BotNote extends TelegramLongPollingBot {

    private static final String CONFIG_FILE_NAME = "config.txt";
    private static final String NOTES_FILE_NAME = "notes.txt";

    private static final String WRITE = "write";
    private static final String SHOW = "show";
    private static final String CONFIG = "config";
    private static final List<String> AVAILABLE_COMMANDS;

    private static String BOT_TOKEN = null;

    static {
        AVAILABLE_COMMANDS = List.of(WRITE, SHOW, CONFIG);
    }

    @Override
    public String getBotUsername() {
        return "ToSaveNotesBot";
    }

    @Override
    public String getBotToken() {

        if (BOT_TOKEN != null) {
            return BOT_TOKEN;
        }

        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("token.txt");
        if (inputStream == null) {
            throw new RuntimeException("Файл с токеном не найден");
        }
        BOT_TOKEN = new Scanner(inputStream, StandardCharsets.UTF_8).next();
        return BOT_TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        Message msg = update.getMessage();
        User user = msg.getFrom();
        Long userId = user.getId();
        String msgText = msg.getText();

        System.out.printf("%s (id = %s) написал: %s%n", user.getFirstName(), user.getId(), msgText);

        if (msgText.startsWith(WRITE)) {
            saveToNotes(userId, msgText);
            return;
        }

        if (msgText.equals(SHOW)) {
            List<String> fileContent = getFileContent(NOTES_FILE_NAME);
            sendText(userId, String.join("\n", fileContent));
            return;
        }

        if (msgText.equals(CONFIG)) {
            List<String> fileContent = getFileContent(CONFIG_FILE_NAME);
            sendText(userId, "Настройки:\n" + String.join("\n", fileContent));
            return;
        }

        sendText(userId, "Неизвестная команда. \nДоступные команды: " + String.join(", ", AVAILABLE_COMMANDS));
    }

    private List<String> getFileContent(String fileName) {
        ClassLoader ctxLoader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = ctxLoader.getResourceAsStream(fileName);
        if (inputStream == null) return new ArrayList<>();

        List<String> lines = new ArrayList<>();
        try (InputStreamReader streamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(streamReader)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lines;
    }

    private void saveToNotes(Long userId, String msgText) {
        ClassLoader ctxLoader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = ctxLoader.getResourceAsStream(CONFIG_FILE_NAME);
        if (inputStream == null) {
            return;
        }

        List<String> lines = getFileContent(CONFIG_FILE_NAME);
        Map<String, String> configValues = new HashMap<>();
        lines.forEach(line -> {
            String[] split = line.split("=");
            configValues.put(split[0].trim(), split[1].trim());
        });

        String pathToFileNotesResources = configValues.get("file-path-to-notes-in-resources");
        String pathToFileNotesCompiled = configValues.get("file-path-to-notes-in-compiled-classes");

        // запись в файл notes, лежащий в src/main/java/resources
        CompletableFuture.runAsync(() -> writeNewLineToFile(pathToFileNotesResources + NOTES_FILE_NAME, msgText));
        // запись в файл notes, лежащий в уже сгенерированном архиве - target/classes
        CompletableFuture.runAsync(() -> writeNewLineToFile(pathToFileNotesCompiled + NOTES_FILE_NAME, msgText));

        sendText(userId, "Done");
    }

    private void writeNewLineToFile(String fullPathToFile, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(fullPathToFile, true));
            writer.append("\n");
            writer.append(String.format("[%s] ", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"))));
            writer.append(text.substring(WRITE.length()).trim()); // отсекаем часть "write "
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Отправка сообщения пользователю
    private void sendText(Long who, String what) {
        SendMessage sm = SendMessage.builder()
                .chatId(who.toString()) // Кому отправляем сообщение
                .text(what).build();    // Содержание сообщения
        try {
            execute(sm);
        } catch (TelegramApiException e) {
            System.out.println("exception = " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
