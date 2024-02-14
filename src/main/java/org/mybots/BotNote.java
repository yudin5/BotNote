package org.mybots;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class BotNote extends TelegramLongPollingBot {

    private static final String CONFIG_FILE_NAME = "config.txt";
    private static String BOT_TOKEN = null;

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

        if (msgText.startsWith("write")) {
            saveToNotes(userId, msgText);
            return;
        }

        if (msgText.equals("show")) {
            List<String> fileContent = getFileContent("notes.txt");
            sendText(userId, String.join("\n", fileContent));
            return;
        }

        if (msgText.equals("config")) {
            List<String> fileContent = getFileContent(CONFIG_FILE_NAME);
            sendText(userId, "Настройки:\n" + String.join("\n", fileContent));
        }
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
        CompletableFuture.runAsync(() -> {
            try {
                ClassLoader ctxLoader = Thread.currentThread().getContextClassLoader();
                InputStream inputStream = ctxLoader.getResourceAsStream(CONFIG_FILE_NAME);
                if (inputStream == null) return;

                List<String> lines = getFileContent(CONFIG_FILE_NAME);

                Map<String, String> configValues = new HashMap<>();
                lines.forEach(line -> {
                    String[] split = line.split("=");
                    configValues.put(split[0].trim(), split[1].trim());
                });
                String fileInResourcesToWrite = configValues.get("file-path-to-notes-in-resources");
                String fileInCompiledClassesToWrite = configValues.get("file-path-to-notes-in-compiled-classes");

                System.out.println("fileInResourcesToWrite = " + fileInResourcesToWrite);
                System.out.println("fileInCompiledClassesToWrite = " + fileInCompiledClassesToWrite);

                System.out.println("configValues = " + configValues);

                BufferedWriter writer1 = new BufferedWriter(new FileWriter(fileInResourcesToWrite, true));
                BufferedWriter writer2 = new BufferedWriter(new FileWriter(fileInCompiledClassesToWrite, true));

                writer1.append("\n");
                writer2.append("\n");
                writer1.append(msgText.substring(6).trim());
                writer2.append(msgText.substring(6).trim());
                writer1.close();
                writer2.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        sendText(userId, "Done");
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
