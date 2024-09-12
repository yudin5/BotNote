package org.mybots;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        BotNote botNote = new BotNote();
        botsApi.registerBot(botNote);
        System.out.printf("Бот запущен. Вы можете найти его в поиске Telegram по имени '%s'\n", botNote.getBotUsername());
    }
}