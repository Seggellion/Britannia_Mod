package com.seggellion.britannia_mod.chess;

import java.util.Locale;

public enum ChessPiece {
    EMPTY(false, PieceType.EMPTY),
    BLACK_ROOK(false, PieceType.ROOK),
    BLACK_KNIGHT(false, PieceType.KNIGHT),
    BLACK_BISHOP(false, PieceType.BISHOP),
    BLACK_QUEEN(false, PieceType.QUEEN),
    BLACK_KING(false, PieceType.KING),
    BLACK_PAWN(false, PieceType.PAWN),
    WHITE_ROOK(true, PieceType.ROOK),
    WHITE_KNIGHT(true, PieceType.KNIGHT),
    WHITE_BISHOP(true, PieceType.BISHOP),
    WHITE_QUEEN(true, PieceType.QUEEN),
    WHITE_KING(true, PieceType.KING),
    WHITE_PAWN(true, PieceType.PAWN);

    public enum PieceType {
        EMPTY,
        KING,
        QUEEN,
        ROOK,
        BISHOP,
        KNIGHT,
        PAWN
    }

    private final boolean white;
    private final PieceType pieceType;

    ChessPiece(boolean white, PieceType pieceType) {
        this.white = white;
        this.pieceType = pieceType;
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }

    public boolean isWhite() {
        return white;
    }

    public PieceType pieceType() {
        return pieceType;
    }

    public String textureName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ChessPiece byId(int id) {
        ChessPiece[] pieces = values();
        if (id < 0 || id >= pieces.length) {
            return EMPTY;
        }
        return pieces[id];
    }
}
