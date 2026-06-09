package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.chess.ChessGameState;
import com.seggellion.britannia_mod.chess.ChessPiece;
import com.seggellion.britannia_mod.network.payload.ChessBoardMoveC2SPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public class ChessBoardScreen extends Screen {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/chess/chess_background.png");
    private static final int PANEL_WIDTH = 256;
    private static final int PANEL_HEIGHT = 192;
    private static final int BOARD_SIZE = 128;
    private static final int CELL_SIZE = BOARD_SIZE / ChessGameState.SIZE;
    private static final int DRAWER_CELL = 12;

    private final BlockPos boardPos;
    private ChessGameState state;
    private int selectedX = -1;
    private int selectedY = -1;

    public ChessBoardScreen(BlockPos boardPos, ChessGameState state) {
        super(Component.literal("Chess Board"));
        this.boardPos = boardPos;
        this.state = state;
    }

    public void updateState(ChessGameState state) {
        this.state = state;
        this.selectedX = -1;
        this.selectedY = -1;
    }

    @Override
    protected void init() {
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;

        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> ChessBoardMoveC2SPayload.sendReset(boardPos))
                .bounds(panelX + PANEL_WIDTH - 58, panelY + 8, 48, 18)
                .build());
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBg(guiGraphics);
        renderDrawers(guiGraphics);
        renderBoard(guiGraphics);
        renderPieces(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderBg(GuiGraphics guiGraphics) {
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        guiGraphics.blit(BACKGROUND, panelX, panelY, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
        guiGraphics.drawCenteredString(font, state.isWhiteTurn() ? "White" : "Black", panelX + PANEL_WIDTH / 2, panelY + 10, 0xF2D598);
    }

    private void renderDrawers(GuiGraphics guiGraphics) {
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        renderCaptured(guiGraphics, state.capturedWhite(), panelX + PANEL_WIDTH - 24, panelY + 34);
        renderCaptured(guiGraphics, state.capturedBlack(), panelX + 12, panelY + 34);
    }

    private void renderCaptured(GuiGraphics guiGraphics, List<ChessPiece> captures, int x, int y) {
        for (int i = 0; i < captures.size(); i++) {
            int drawX = x + (i % 2) * DRAWER_CELL;
            int drawY = y + (i / 2) * DRAWER_CELL;
            renderPiece(guiGraphics, captures.get(i), drawX, drawY, DRAWER_CELL, 0xFFFFFFFF);
        }
    }

    private void renderBoard(GuiGraphics guiGraphics) {
        int boardX = boardX();
        int boardY = boardY();
        for (int y = 0; y < ChessGameState.SIZE; y++) {
            for (int x = 0; x < ChessGameState.SIZE; x++) {
                int color = ((x + y) & 1) == 0 ? 0xFFB7925A : 0xFF684322;
                guiGraphics.fill(boardX + x * CELL_SIZE, boardY + y * CELL_SIZE,
                        boardX + (x + 1) * CELL_SIZE, boardY + (y + 1) * CELL_SIZE, color);
            }
        }

        if (selectedX >= 0 && selectedY >= 0) {
            int x = boardX + selectedX * CELL_SIZE;
            int y = boardY + selectedY * CELL_SIZE;
            guiGraphics.fill(x, y, x + CELL_SIZE, y + CELL_SIZE, 0x55FFF0A8);
        }
    }

    private void renderPieces(GuiGraphics guiGraphics) {
        int boardX = boardX();
        int boardY = boardY();
        for (int y = 0; y < ChessGameState.SIZE; y++) {
            for (int x = 0; x < ChessGameState.SIZE; x++) {
                ChessPiece piece = state.get(x, y);
                if (!piece.isEmpty()) {
                    renderPiece(guiGraphics, piece, boardX + x * CELL_SIZE, boardY + y * CELL_SIZE, CELL_SIZE, 0xFFFFFFFF);
                }
            }
        }
    }

    private void renderPiece(GuiGraphics guiGraphics, ChessPiece piece, int x, int y, int size, int tint) {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                "britannia_mod",
                "textures/screens/chess/pieces/" + piece.textureName() + ".png"
        );
        guiGraphics.blit(texture, x, y, 0, 0, size, size, size, size);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int cellX = (int) ((mouseX - boardX()) / CELL_SIZE);
            int cellY = (int) ((mouseY - boardY()) / CELL_SIZE);
            if (cellX >= 0 && cellX < ChessGameState.SIZE && cellY >= 0 && cellY < ChessGameState.SIZE) {
                handleBoardClick(cellX, cellY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleBoardClick(int x, int y) {
        ChessPiece clicked = state.get(x, y);
        if (selectedX < 0) {
            if (!clicked.isEmpty() && clicked.isWhite() == state.isWhiteTurn()) {
                selectedX = x;
                selectedY = y;
            }
            return;
        }

        if (selectedX == x && selectedY == y) {
            selectedX = -1;
            selectedY = -1;
            return;
        }

        ChessBoardMoveC2SPayload.send(boardPos, selectedX, selectedY, x, y);
    }

    private int boardX() {
        return (width - BOARD_SIZE) / 2;
    }

    private int boardY() {
        return (height - PANEL_HEIGHT) / 2 + 36;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
