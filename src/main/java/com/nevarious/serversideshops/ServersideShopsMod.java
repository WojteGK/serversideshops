package com.nevarious.serversideshops;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.nevarious.serversideshops.EconomyService;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ServersideShopsMod implements ModInitializer {
    public static final String MOD_ID = "serversideshops";
    public static final String OBJECTIVE = "money";
    public static ShopConfig CONFIG;
    public static final Map<UUID, ShopSession> SESSIONS = new HashMap<>();

    @Override
    public void onInitialize() {
        CONFIG = ShopConfig.loadOrCreate();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("balance")
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        long bal = EconomyService.getBalance(player);
                        player.sendMessage(Text.literal("Balance: $" + bal), false);
                        return 1;
                    })
                    .then(CommandManager.argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                // simple lookup by name (online only for brevity)
                                String name = StringArgumentType.getString(ctx, "player");
                                MinecraftServer server = ctx.getSource().getServer();
                                ServerPlayerEntity target = server.getPlayerManager().getPlayer(name);
                                if (target == null) {
                                    ctx.getSource().sendError(Text.literal("Player not found."));
                                    return 0;
                                }
                                long bal = EconomyService.getBalance(target);
                                ctx.getSource().sendMessage(Text.literal(target.getName().getString() + " balance: $" + bal));
                                return 1;
                            }))
            );

            dispatcher.register(CommandManager.literal("pay")
                    .then(CommandManager.argument("target", StringArgumentType.word())
                            .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                    .executes(ctx -> {
                                        ServerPlayerEntity payer = ctx.getSource().getPlayer();
                                        String targetName = StringArgumentType.getString(ctx, "target");
                                        int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                        ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);
                                        if (target == null) {
                                            ctx.getSource().sendError(Text.literal("Target offline."));
                                            return 0;
                                        }
                                        if (EconomyService.tryTransfer(payer, target, amount)) {
                                            payer.sendMessage(Text.literal("Paid $" + amount + " to " + targetName), false);
                                            target.sendMessage(Text.literal("You received $" + amount + " from " + payer.getName().getString()), false);
                                            return 1;
                                        } else {
                                            payer.sendMessage(Text.literal("Insufficient funds."), false);
                                            return 0;
                                        }
                                    }))));

            dispatcher.register(CommandManager.literal("shop")
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        ShopGui.openCategories(player);
                        return 1;
                    })
                    .then(CommandManager.argument("category", StringArgumentType.word())
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                String category = StringArgumentType.getString(ctx, "category");
                                ShopGui.openCategory(player, category, 0, ShopSession.Mode.BUY);
                                return 1;
                            }))
                    .then(CommandManager.literal("reload")
                            .requires(src -> src.hasPermissionLevel(2))
                            .executes(ctx -> {
                                CONFIG = ShopConfig.loadOrCreate(); // re-read
                                ctx.getSource().sendMessage(Text.literal("serversideshops config reloaded."));
                                return 1;
                            }))
            );

            dispatcher.register(CommandManager.literal("buy")
                    .then(CommandManager.argument("item_id", StringArgumentType.word())
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                String id = StringArgumentType.getString(ctx, "item_id");
                                return ShopActions.buy(player, id, 1);
                            })
                            .then(CommandManager.argument("qty", IntegerArgumentType.integer(1))
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                                        String id = StringArgumentType.getString(ctx, "item_id");
                                        int qty = IntegerArgumentType.getInteger(ctx, "qty");
                                        return ShopActions.buy(player, id, qty);
                                    }))));
            dispatcher.register(CommandManager.literal("sell")
                    .then(CommandManager.argument("item_id", StringArgumentType.word())
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                String id = StringArgumentType.getString(ctx, "item_id");
                                return ShopActions.sell(player, id, 1);
                            })
                            .then(CommandManager.argument("qty", IntegerArgumentType.integer(1))
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                                        String id = StringArgumentType.getString(ctx, "item_id");
                                        int qty = IntegerArgumentType.getInteger(ctx, "qty");
                                        return ShopActions.sell(player, id, qty);
                                    }))));
        });
    }
}
