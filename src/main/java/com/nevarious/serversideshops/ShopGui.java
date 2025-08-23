package com.nevarious.serversideshops;

import com.mojang.authlib.GameProfile;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ShopGui {

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;

    // ----- Public API -----

    public static void openCategories(ServerPlayerEntity player) {
        Inventory inv = new SimpleInventory(SIZE);
        // Open once with a stable title to avoid mouse recenter
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInv, p) ->
                new ShopScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, ROWS), Text.literal("Shop")));
        // Initialize session and fill contents
        ServersideShopsMod.SESSIONS.put(player.getUuid(),
                new ShopSession(player.getUuid(), null, 0, ShopSession.Mode.BUY));
        fillCategories(inv);
        player.currentScreenHandler.sendContentUpdates();
    }

    public static void openCategory(ServerPlayerEntity player, String categoryId, int page, ShopSession.Mode mode) {
        // Keep the same handler/screen; just fill it
        if (!(player.currentScreenHandler instanceof ShopScreenHandler)) {
            openCategories(player); // fallback if somehow not open
        }
        ServersideShopsMod.SESSIONS.put(player.getUuid(),
                new ShopSession(player.getUuid(), categoryId, page, mode));
        Inventory inv = ((ShopScreenHandler) player.currentScreenHandler).menuInventory;
        fillCategory(inv, categoryId, page, mode);
        player.currentScreenHandler.sendContentUpdates();
    }

    // ----- Fillers (mutate the existing inventory) -----

    private static void fillCategories(Inventory inv) {
        // clear
        for (int s = 0; s < SIZE; s++) inv.setStack(s, ItemStack.EMPTY);

        int i = 0;
        for (ShopConfig.Category cat : ServersideShopsMod.CONFIG.categories) {
            String[] icon = cat.icon.split(":");
            Item iconItem = Registries.ITEM.get(Identifier.of(icon[0], icon[1]));
            ItemStack stack = new ItemStack(iconItem);
            stack.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Category: " + cat.name).styled(st -> st.withBold(true).withItalic(false)));
            inv.setStack(i++, stack);
        }

        // Close button (barrier) — bold, dark red
        inv.setStack(53, iconBarrier(
                Text.literal("Close").styled(s -> s.withBold(true).withItalic(false).withColor(0xAA0000))
        ));
    }

    private static void fillCategory(Inventory inv, String categoryId, int page, ShopSession.Mode mode) {
        // clear
        for (int s = 0; s < SIZE; s++) inv.setStack(s, ItemStack.EMPTY);

        ShopConfig.Category cat = ServersideShopsMod.CONFIG.categories.stream()
                .filter(c -> Objects.equals(c.id, categoryId)).findFirst().orElse(null);
        if (cat == null) return;

        List<String> ids = cat.items != null ? cat.items : new ArrayList<>();
        int start = page * 45;
        int end = Math.min(ids.size(), start + 45);
        int slot = 0;

        for (int idx = start; idx < end; idx++) {
            String itemId = ids.get(idx);
            ShopConfig.ShopItem def = ServersideShopsMod.CONFIG.items.get(itemId);
            if (def == null) continue;

            String[] icon = def.icon.split(":");
            Item iconItem = Registries.ITEM.get(Identifier.of(icon[0], icon[1]));
            ItemStack stack = new ItemStack(iconItem);
            long price = (mode == ShopSession.Mode.BUY) ? def.price_buy : (def.price_sell == null ? 0 : def.price_sell);
            stack.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal(itemId + " $" + price).styled(s -> s.withBold(true).withItalic(false)));
            inv.setStack(slot++, stack);
        }

        // Nav row — bold + colored
        inv.setStack(45, iconMHF("MHF_ArrowLeft",
                Text.literal("« Prev").styled(s -> s.withBold(true).withItalic(false).withColor(0xFF5555)))); // red
        inv.setStack(46, iconMHF("MHF_Backward",
                Text.literal("Back").styled(s -> s.withBold(true).withItalic(false).withColor(0xFFFF55))));   // yellow

        ItemStack modeStack = (mode == ShopSession.Mode.BUY)
                ? iconWool(true, Text.literal("Mode: BUY").styled(s -> s.withBold(true).withItalic(false).withColor(0x55FF55)))  // green
                : iconWool(false, Text.literal("Mode: SELL").styled(s -> s.withBold(true).withItalic(false).withColor(0xFF5555))); // red
        inv.setStack(52, modeStack);

        inv.setStack(53, iconMHF("MHF_ArrowRight",
                Text.literal("Next »").styled(s -> s.withBold(true).withItalic(false).withColor(0x55FF55)))); // green
    }

    // ----- Icon helpers -----

    private static ItemStack iconWool(boolean green, Text label) {
        ItemStack stack = new ItemStack(green ? Items.GREEN_WOOL : Items.RED_WOOL);
        stack.set(DataComponentTypes.CUSTOM_NAME, label);
        return stack;
    }

    private static ItemStack iconBarrier(Text label) {
        ItemStack stack = new ItemStack(Items.BARRIER);
        stack.set(DataComponentTypes.CUSTOM_NAME, label);
        return stack;
    }

    private static ItemStack iconMHF(String mhfName, Text label) {
        try {
            // Stable "offline" UUID (no network needed)
            java.util.UUID id = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + mhfName)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            GameProfile gp = new GameProfile(id, mhfName);
    
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponentTypes.CUSTOM_NAME, label);
            head.set(DataComponentTypes.PROFILE, new ProfileComponent(gp));
            return head;
        } catch (Exception e) {
            // Fallback icon if profile fails (e.g., strict offline mode)
            ItemStack fallback = new ItemStack(Items.ARROW);
            fallback.set(DataComponentTypes.CUSTOM_NAME, label);
            return fallback;
        }
    }
    

    // ----- ScreenHandler that keeps a reference to the menu inventory -----

    public static class ShopScreenHandler extends GenericContainerScreenHandler {
        final Inventory menuInventory;

        public ShopScreenHandler(ScreenHandlerType<?> type, int syncId, PlayerInventory playerInventory, Inventory inventory, int rows) {
            super(type, syncId, playerInventory, inventory, rows);
            this.menuInventory = inventory;
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, net.minecraft.entity.player.PlayerEntity player) {
            if (!(player instanceof ServerPlayerEntity sp)) {
                super.onSlotClick(slotIndex, button, actionType, player);
                return;
            }
            if (slotIndex >= 0 && slotIndex < SIZE) {
                ItemStack clicked = this.getSlot(slotIndex).getStack();
                if (!clicked.isEmpty()) {
                    String name = clicked.getName().getString();
                    ShopSession s = ServersideShopsMod.SESSIONS.get(sp.getUuid());

                    if (name.startsWith("Category: ")) {
                        String catName = name.substring("Category: ".length());
                        String catId = null;
                        for (ShopConfig.Category c : ServersideShopsMod.CONFIG.categories) {
                            if (c.name.equals(catName)) { catId = c.id; break; }
                        }
                        if (catId != null) {
                            ServersideShopsMod.SESSIONS.put(sp.getUuid(), new ShopSession(sp.getUuid(), catId, 0, ShopSession.Mode.BUY));
                            fillCategory(menuInventory, catId, 0, ShopSession.Mode.BUY);
                            this.sendContentUpdates();
                        }
                        return;
                    }
                    if ("Back".equals(name)) {
                        ServersideShopsMod.SESSIONS.put(sp.getUuid(), new ShopSession(sp.getUuid(), null, 0, ShopSession.Mode.BUY));
                        fillCategories(menuInventory);
                        this.sendContentUpdates();
                        return;
                    }
                    if ("« Prev".equals(name)) {
                        if (s != null && s.category != null && s.page > 0) {
                            s.page -= 1;
                            ServersideShopsMod.SESSIONS.put(sp.getUuid(), s);
                            fillCategory(menuInventory, s.category, s.page, s.mode);
                            this.sendContentUpdates();
                        }
                        return;
                    }
                    if ("Next »".equals(name)) {
                        if (s != null && s.category != null) {
                            s.page += 1;
                            ServersideShopsMod.SESSIONS.put(sp.getUuid(), s);
                            fillCategory(menuInventory, s.category, s.page, s.mode);
                            this.sendContentUpdates();
                        }
                        return;
                    }
                    if ("Close".equals(name)) { sp.closeHandledScreen(); return; }
                    if (name.startsWith("Mode: ")) {
                        if (s != null && s.category != null) {
                            s.mode = (s.mode == ShopSession.Mode.BUY) ? ShopSession.Mode.SELL : ShopSession.Mode.BUY;
                            ServersideShopsMod.SESSIONS.put(sp.getUuid(), s);
                            fillCategory(menuInventory, s.category, s.page, s.mode);
                            this.sendContentUpdates();
                        }
                        return;
                    }

                    int space = name.indexOf(' ');
                    if (space > 0) {
                        String itemId = name.substring(0, space);
                        if (s != null && s.mode == ShopSession.Mode.SELL) {
                            ShopActions.sell(sp, itemId, 1);
                        } else {
                            ShopActions.buy(sp, itemId, 1);
                        }
                        return;
                    }
                }
            return;
            }
        }
    }
}
