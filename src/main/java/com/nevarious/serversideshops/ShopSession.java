package com.nevarious.serversideshops;

import java.util.UUID;

public class ShopSession {
    public enum Mode { BUY, SELL }
    public UUID player;
    public String category;
    public int page;
    public Mode mode;

    public ShopSession(UUID player, String category, int page, Mode mode) {
        this.player = player; this.category = category; this.page = page; this.mode = mode;
    }
}
