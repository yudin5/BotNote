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
    private static boolean SCHEDULER_ON = false;

    //    private static final Set<Long> stopList = Set.of(349647547L);
    private static final Set<Long> stopList = Set.of();

    private static final Long MY_USER_ID = 528495430L;

    private static final String CONFIG = "/config";
    private static final String WRITE = "write";
    private static final String SHOW = "/show";
    private static final String START = "/start";

    private static final String PATH_TO_FILE_IN_RESOURCES = "file-path-to-notes-in-resources";
    private static final String PATH_TO_FILE_COMPILED = "file-path-to-notes-in-compiled-classes";

    private static final List<String> AVAILABLE_COMMANDS;

    private static String BOT_TOKEN = null;

    static {
        AVAILABLE_COMMANDS = List.of(WRITE, SHOW);
    }

    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");


    @Override
    public String getBotUsername() {
        return "ToSaveNotesBot";
    }

    @Override
    public String getBotToken() {

        if (!SCHEDULER_ON) {
            startScheduler();
        }

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

        System.out.printf("%s - %s (id = %s) написал: %s%n",
                LocalDateTime.now().format(dateTimeFormatter),
                user.getFirstName(),
                user.getId(),
                msgText);

        if (stopList.contains(userId)) {
            sendText(userId, "Доступ запрещён, хехе");
            return;
        }

        if (msg.isCommand()) {
            // При запуске
            if (msgText.equals(START)) {
                sendText(userId, "Добрый день! \uD83E\uDD1D");
                sendText(userId, "Я - бот для сохранения общих заметок. ✍ Используйте МЕНЮ для отправки мне команды, чтобы просмотреть заметки. " +
                        "Я не умею общаться, сообщения будут игнорироваться.");
                sendText(userId, "Чтобы создать новую заметку, напишите простое сообщение, начав его со слова 'write'.");
                sendText(userId, "Поехали? \uD83D\uDE80");
                return;
            }
            // Показать заметки
            if (msgText.equals(SHOW)) {
                List<String> fileContent = getFileContent(NOTES_FILE_NAME);
                sendText(userId, String.join("\n", fileContent));
                return;
            }
            // Показать конфиг. Доступно только мне
            if (msgText.equals(CONFIG)) {
                if (userId.equals(MY_USER_ID)) {
                    List<String> fileContent = getFileContent(CONFIG_FILE_NAME);
                    sendText(userId, "Настройки:\n" + String.join("\n", fileContent));
                    return;
                } else {
                    sendText(userId, "Извините, но настройки я могу показать лишь владельцу бота");
                    return;
                }
            }
        }

        if (msgText == null) {
            sendText(userId, "Вы отправили файл? Я не знаю, что с ним делать..");
            return;
        }

        // Добавить заметку
        if (msgText.startsWith(WRITE)) {
            saveToNotes(msgText);
            sendText(userId, "Заметка сохранена");
            return;
        }

        sendText(userId, "Неизвестная команда. \nДоступные команды: " + String.join(", ", AVAILABLE_COMMANDS)
                + "\nЧтобы сохранить новую заметку, начните сообщение со слова 'write'");
    }

    private void startScheduler() {
        SCHEDULER_ON = true;
//        CompletableFuture.runAsync(() -> {
//            while (true) {
//                try {
//                    Thread.sleep(15000L);
//                    System.out.println("Планировщик запущен");
//                    if (LocalDateTime.now().isAfter(LocalDateTime.of(2024, Month.MAY, 6, 16, 47))) {
//                        sendText(MY_USER_ID, "Пора на встречу!");
//                    }
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }
//            }
//        });
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

    private void saveToNotes(String msgText) {
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

        String pathToFileNotesResources = configValues.get(PATH_TO_FILE_IN_RESOURCES);
        String pathToFileNotesCompiled = configValues.get(PATH_TO_FILE_COMPILED);

        // Запись в файл notes, лежащий в src/main/java/resources
        CompletableFuture.runAsync(() -> writeNewLineToFile(pathToFileNotesResources + NOTES_FILE_NAME, msgText));
        // Запись в файл notes, лежащий в уже сгенерированном архиве - target/classes
        CompletableFuture.runAsync(() -> writeNewLineToFile(pathToFileNotesCompiled + NOTES_FILE_NAME, msgText));
    }

    private void writeNewLineToFile(String fullPathToFile, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(fullPathToFile, true));
            writer.append("\n");
            writer.append(String.format("[%s] ", LocalDateTime.now().format(dateTimeFormatter)));
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
