package com.seggellion.britannia_mod.chess;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChessGameState {
    public static final int SIZE = 8;
    public static final int SQUARES = SIZE * SIZE;

    private final ChessPiece[] board = new ChessPiece[SQUARES];
    private final List<ChessPiece> capturedWhite = new ArrayList<>();
    private final List<ChessPiece> capturedBlack = new ArrayList<>();
    private boolean whiteTurn = true;

    public ChessGameState() {
        reset();
    }

    public static ChessGameState copyOf(ChessGameState other) {
        ChessGameState copy = new ChessGameState();
        System.arraycopy(other.board, 0, copy.board, 0, SQUARES);
        copy.capturedWhite.clear();
        copy.capturedWhite.addAll(other.capturedWhite);
        copy.capturedBlack.clear();
        copy.capturedBlack.addAll(other.capturedBlack);
        copy.whiteTurn = other.whiteTurn;
        return copy;
    }

    public void reset() {
        for (int i = 0; i < SQUARES; i++) {
            board[i] = ChessPiece.EMPTY;
        }
        capturedWhite.clear();
        capturedBlack.clear();
        whiteTurn = true;

        ChessPiece[] blackBackRank = {
                ChessPiece.BLACK_ROOK, ChessPiece.BLACK_KNIGHT, ChessPiece.BLACK_BISHOP, ChessPiece.BLACK_QUEEN,
                ChessPiece.BLACK_KING, ChessPiece.BLACK_BISHOP, ChessPiece.BLACK_KNIGHT, ChessPiece.BLACK_ROOK
        };
        ChessPiece[] whiteBackRank = {
                ChessPiece.WHITE_ROOK, ChessPiece.WHITE_KNIGHT, ChessPiece.WHITE_BISHOP, ChessPiece.WHITE_QUEEN,
                ChessPiece.WHITE_KING, ChessPiece.WHITE_BISHOP, ChessPiece.WHITE_KNIGHT, ChessPiece.WHITE_ROOK
        };

        for (int y = 0; y < SIZE; y++) {
            set(0, y, blackBackRank[y]);
            set(1, y, ChessPiece.BLACK_PAWN);
            set(6, y, ChessPiece.WHITE_PAWN);
            set(7, y, whiteBackRank[y]);
        }
    }

    public ChessPiece get(int x, int y) {
        if (!isInside(x, y)) {
            return ChessPiece.EMPTY;
        }
        return board[index(x, y)];
    }

    public boolean isWhiteTurn() {
        return whiteTurn;
    }

    public List<ChessPiece> capturedWhite() {
        return Collections.unmodifiableList(capturedWhite);
    }

    public List<ChessPiece> capturedBlack() {
        return Collections.unmodifiableList(capturedBlack);
    }

    public boolean move(int fromX, int fromY, int toX, int toY) {
        if (!isInside(fromX, fromY) || !isInside(toX, toY) || (fromX == toX && fromY == toY)) {
            return false;
        }

        ChessPiece piece = get(fromX, fromY);
        ChessPiece target = get(toX, toY);
        if (piece.isEmpty() || piece.isWhite() != whiteTurn || (!target.isEmpty() && target.isWhite() == piece.isWhite())) {
            return false;
        }

        if (!isLegalMove(piece, fromX, fromY, toX, toY, target)) {
            return false;
        }

        if (!target.isEmpty()) {
            if (target.isWhite()) {
                capturedWhite.add(target);
            } else {
                capturedBlack.add(target);
            }
        }

        set(toX, toY, promoteIfNeeded(piece, toX));
        set(fromX, fromY, ChessPiece.EMPTY);
        whiteTurn = !whiteTurn;
        return true;
    }

    private ChessPiece promoteIfNeeded(ChessPiece piece, int toX) {
        if (piece == ChessPiece.WHITE_PAWN && toX == 0) return ChessPiece.WHITE_QUEEN;
        if (piece == ChessPiece.BLACK_PAWN && toX == SIZE - 1) return ChessPiece.BLACK_QUEEN;
        return piece;
    }

    private boolean isLegalMove(ChessPiece piece, int fromX, int fromY, int toX, int toY, ChessPiece target) {
        int dx = toX - fromX;
        int dy = toY - fromY;
        int adx = Math.abs(dx);
        int ady = Math.abs(dy);

        return switch (piece.pieceType()) {
            case KING -> adx <= 1 && ady <= 1;
            case QUEEN -> (dx == 0 || dy == 0 || adx == ady) && clearPath(fromX, fromY, toX, toY);
            case ROOK -> (dx == 0 || dy == 0) && clearPath(fromX, fromY, toX, toY);
            case BISHOP -> adx == ady && clearPath(fromX, fromY, toX, toY);
            case KNIGHT -> (adx == 1 && ady == 2) || (adx == 2 && ady == 1);
            case PAWN -> legalPawnMove(piece, fromX, fromY, dx, dy, target);
            case EMPTY -> false;
        };
    }

    private boolean legalPawnMove(ChessPiece piece, int fromX, int fromY, int dx, int dy, ChessPiece target) {
        int direction = piece.isWhite() ? -1 : 1;
        int startX = piece.isWhite() ? 6 : 1;
        if (dy == 0 && dx == direction && target.isEmpty()) {
            return true;
        }
        if (dy == 0 && fromX == startX && dx == direction * 2 && target.isEmpty()) {
            return get(fromX + direction, fromY).isEmpty();
        }
        return Math.abs(dy) == 1 && dx == direction && !target.isEmpty() && target.isWhite() != piece.isWhite();
    }

    private boolean clearPath(int fromX, int fromY, int toX, int toY) {
        int stepX = Integer.compare(toX, fromX);
        int stepY = Integer.compare(toY, fromY);
        int x = fromX + stepX;
        int y = fromY + stepY;

        while (x != toX || y != toY) {
            if (!get(x, y).isEmpty()) {
                return false;
            }
            x += stepX;
            y += stepY;
        }
        return true;
    }

    private void set(int x, int y, ChessPiece piece) {
        board[index(x, y)] = piece;
    }

    private static boolean isInside(int x, int y) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
    }

    private static int index(int x, int y) {
        return y * SIZE + x;
    }

    public byte[] boardBytes() {
        byte[] bytes = new byte[SQUARES];
        for (int i = 0; i < SQUARES; i++) {
            bytes[i] = (byte) board[i].ordinal();
        }
        return bytes;
    }

    public static ChessGameState fromBytes(byte[] boardBytes, byte[] whiteCaptures, byte[] blackCaptures, boolean whiteTurn) {
        ChessGameState state = new ChessGameState();
        for (int i = 0; i < SQUARES; i++) {
            state.board[i] = i < boardBytes.length ? ChessPiece.byId(boardBytes[i]) : ChessPiece.EMPTY;
        }
        state.capturedWhite.clear();
        for (byte capture : whiteCaptures) {
            ChessPiece piece = ChessPiece.byId(capture);
            if (!piece.isEmpty()) state.capturedWhite.add(piece);
        }
        state.capturedBlack.clear();
        for (byte capture : blackCaptures) {
            ChessPiece piece = ChessPiece.byId(capture);
            if (!piece.isEmpty()) state.capturedBlack.add(piece);
        }
        state.whiteTurn = whiteTurn;
        return state;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeByteArray(boardBytes());
        buf.writeByteArray(toBytes(capturedWhite));
        buf.writeByteArray(toBytes(capturedBlack));
        buf.writeBoolean(whiteTurn);
    }

    public static ChessGameState read(FriendlyByteBuf buf) {
        return fromBytes(buf.readByteArray(SQUARES), buf.readByteArray(32), buf.readByteArray(32), buf.readBoolean());
    }

    public void save(CompoundTag tag) {
        tag.putByteArray("Board", boardBytes());
        tag.putByteArray("CapturedWhite", toBytes(capturedWhite));
        tag.putByteArray("CapturedBlack", toBytes(capturedBlack));
        tag.putBoolean("WhiteTurn", whiteTurn);
    }

    public void load(CompoundTag tag) {
        byte[] savedBoard = tag.getByteArray("Board");
        if (savedBoard.length == SQUARES) {
            for (int i = 0; i < SQUARES; i++) {
                board[i] = ChessPiece.byId(savedBoard[i]);
            }
        } else {
            reset();
        }
        capturedWhite.clear();
        loadCaptures(tag.getByteArray("CapturedWhite"), capturedWhite);
        capturedBlack.clear();
        loadCaptures(tag.getByteArray("CapturedBlack"), capturedBlack);
        whiteTurn = !tag.contains("WhiteTurn") || tag.getBoolean("WhiteTurn");
    }

    private static byte[] toBytes(List<ChessPiece> pieces) {
        byte[] bytes = new byte[pieces.size()];
        for (int i = 0; i < pieces.size(); i++) {
            bytes[i] = (byte) pieces.get(i).ordinal();
        }
        return bytes;
    }

    private static void loadCaptures(byte[] tag, List<ChessPiece> captures) {
        for (byte value : tag) {
            ChessPiece piece = ChessPiece.byId(value);
            if (!piece.isEmpty()) captures.add(piece);
        }
    }
}
