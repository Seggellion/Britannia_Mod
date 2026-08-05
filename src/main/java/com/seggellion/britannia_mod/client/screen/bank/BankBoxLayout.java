package com.seggellion.britannia_mod.client.screen.bank;

/**
 * Milestone 9: where everything on the Bank Box screen goes.
 *
 * <p>The Bank Box is deliberately not a member of the dialogue family (design §9.1): no portrait,
 * no parchment banner, no right-hand button column. It is an inventory screen, drawn as an open
 * strongbox: the owner supplied chest artwork, and the stored-item grid sits literally inside its
 * red velvet interior. Title and weight are written on the open lid; the player's own pack and the
 * controls sit on a plain dark panel beneath the box, because they are not part of the vault.
 *
 * <h2>The chest is fixed-proportion art over a variable-height grid</h2>
 * The grid shows 2–5 rows depending on screen height and content, so the chest cannot be one
 * stretched image -- at two rows the whole illustration would squash to half its aspect. It is
 * drawn as three vertical slices instead: the lid at up to its natural proportion (compressing
 * when height is tight), the velvet interior stretched to exactly the grid's height (velvet
 * tolerates vertical stretch; a lid and lock do not), and the base at fixed height. This class
 * owns those three rectangles; the screen samples the matching bands of the texture into them.
 *
 * <h2>Three things adapt</h2>
 * <ul>
 *   <li><b>Bank grid rows</b>, 2–5, capped by content -- design §9.3 permits this explicitly and
 *       the fixed content leaves no alternative at small scaled heights.</li>
 *   <li><b>Control placement</b>: a column beside the chest when wide, two rows beneath the
 *       hotbar when narrow.</li>
 *   <li><b>The chest itself</b>: below {@value #CHEST_WIDTH}+margins of scaled width there is no
 *       room to draw it at all, and the screen falls back to the plain layout rather than
 *       overflow the panel -- the same yield-to-content rule the dialogue family's portrait
 *       follows at narrow widths.</li>
 * </ul>
 *
 * <p>Plain arithmetic with no client types, so every combination is JUnit-testable
 * (Architecture Decision 0).
 */
public record BankBoxLayout(
        boolean compact,
        boolean chestVisible,
        int panelLeft,
        int panelTop,
        int panelWidth,
        int panelHeight,
        int titleY,
        int weightY,
        int chestLeft,
        int chestTop,
        int chestWidth,
        int chestLidHeight,
        int chestInteriorHeight,
        int chestBaseHeight,
        BankGridGeometry bankGrid,
        int inventoryLabelY,
        BankGridGeometry inventoryGrid,
        BankGridGeometry hotbar,
        int amountBoxX,
        int amountBoxY,
        int amountBoxWidth,
        int denominationY,
        int denominationWidth,
        int actionX,
        int backY,
        int actionWidth,
        int statusY,
        int statusMaxWidth
) {
    public static final int COLUMNS = 9;
    public static final int INVENTORY_ROWS = 3;
    public static final int HOTBAR_ROWS = 1;

    /** Design §9.3 recommends four or more visible rows when space permits. */
    public static final int MAX_BANK_ROWS = 5;
    /** §9.3 requires the grid stay multi-row, so this is the floor even when space is tight. */
    public static final int MIN_BANK_ROWS = 2;

    public static final int MARGIN = 8;
    public static final int SECTION_GAP = 6;
    /** Vanilla's gap between the main inventory and the hotbar. */
    public static final int HOTBAR_GAP = 4;
    public static final int ROW_HEIGHT = 20;
    public static final int ROW_GAP = 4;
    /**
     * In the wide arrangement the three denomination buttons are stacked as full-width rows, not
     * placed side by side: a third of any credible column is too narrow for "Copper" (108/3 with
     * gaps is ~33px against the label's ~46px), and the column is a vertical stack everywhere
     * else -- amount, Back and Withdraw are already full-width rows. Compact mode keeps them side
     * by side because there they share the full content width, where a third genuinely fits.
     */
    public static final int CONTROL_COLUMN_WIDTH = 108;

    private static final int GRID_WIDTH = COLUMNS * BankGridGeometry.DEFAULT_CELL_PITCH;

    // ---- Chest calibration, matched to the owner's artwork (drawn at 304x396). ----
    // The grid is fixed-width, so the chest is too: sized so the velvet interior region of the
    // art (~80% of its width between the metal walls) holds the grid plus a little padding.

    /** Padding between the interior walls and the grid, inside the velvet. */
    public static final int CHEST_INTERIOR_PAD_X = 4;
    public static final int CHEST_INTERIOR_PAD_TOP = 6;
    /** Slightly deeper below, so items sit "in" the velvet rather than on its front lip. */
    public static final int CHEST_INTERIOR_PAD_BOTTOM = 8;

    /** {@code (GRID_WIDTH + 2*PAD_X) / 0.80}, the interior's fraction of the art's width. */
    public static final int CHEST_WIDTH = ((GRID_WIDTH + (CHEST_INTERIOR_PAD_X * 2)) * 10) / 8;

    /**
     * The open lid at its natural proportion: the lid band is ~42% of the art's height, and the
     * art is 396/304 tall for its width. It may compress below this when the screen is short --
     * a squashed lid reads fine where a squashed lock would not -- but never below room for the
     * two text rows written on it.
     */
    public static final int CHEST_LID_NATURAL = Math.round(CHEST_WIDTH * (396f / 304f) * 0.42f);

    /** The foot of the box and its hasp, ~12% of the art. Fixed: locks do not stretch well. */
    public static final int CHEST_BASE_HEIGHT = Math.round(CHEST_WIDTH * (396f / 304f) * 0.12f);

    /**
     * @param fontLineHeight {@code font.lineHeight}, the only client value this needs
     * @param bankItemCount  how many stored items exist, so the grid stops growing once it could
     *                       show them all
     */
    public static BankBoxLayout calculate(int screenWidth, int screenHeight, int fontLineHeight, int bankItemCount) {
        int textRow = fontLineHeight + 2;
        int pitch = BankGridGeometry.DEFAULT_CELL_PITCH;

        boolean chestVisible = screenWidth >= (MARGIN * 2) + CHEST_WIDTH;
        int contentWidth = chestVisible ? CHEST_WIDTH : GRID_WIDTH;

        int wideWidth = (MARGIN * 2) + contentWidth + SECTION_GAP + CONTROL_COLUMN_WIDTH;
        boolean compact = screenWidth < wideWidth;
        int panelWidth = compact ? (MARGIN * 2) + contentWidth : wideWidth;

        // The lid always has room for the title and weight rows written on it.
        int lidMin = (textRow * 2) + 8;

        // Everything that is not the bank grid, so what remains decides how many rows it gets.
        // The chest replaces the plain layout's title/weight band with its lid and adds its base.
        int aboveGrid = chestVisible ? lidMin + CHEST_INTERIOR_PAD_TOP : (textRow * 2) + SECTION_GAP;
        int belowGrid = chestVisible ? CHEST_INTERIOR_PAD_BOTTOM + CHEST_BASE_HEIGHT : 0;
        int controlsHeight = compact ? (ROW_HEIGHT * 2) + ROW_GAP + SECTION_GAP : 0;
        int fixedHeight = (MARGIN * 2)
                + aboveGrid
                + belowGrid
                + SECTION_GAP + textRow                     // pack label
                + (INVENTORY_ROWS * pitch)
                + HOTBAR_GAP
                + (HOTBAR_ROWS * pitch)
                + controlsHeight
                + SECTION_GAP + textRow;                    // status

        int rowsThatFit = (screenHeight - fixedHeight) / pitch;
        int rowsWorthShowing = Math.max(MIN_BANK_ROWS, BankGridScroll.rowsNeeded(bankItemCount, COLUMNS));
        int bankRows = clamp(Math.min(rowsThatFit, rowsWorthShowing), MIN_BANK_ROWS, MAX_BANK_ROWS);

        // Leftover height goes to the lid, up to its natural proportion -- the art fills toward
        // its true shape on roomy screens and compresses on tight ones.
        int lidHeight = 0;
        int interiorHeight = 0;
        int baseHeight = 0;
        if (chestVisible) {
            int slack = screenHeight - (fixedHeight + (bankRows * pitch));
            lidHeight = clamp(lidMin + Math.max(0, slack), lidMin, CHEST_LID_NATURAL);
            interiorHeight = CHEST_INTERIOR_PAD_TOP + (bankRows * pitch) + CHEST_INTERIOR_PAD_BOTTOM;
            baseHeight = CHEST_BASE_HEIGHT;
        }

        int panelHeight = fixedHeight + (bankRows * pitch) + (chestVisible ? lidHeight - lidMin : 0);
        int panelLeft = Math.max(0, (screenWidth - panelWidth) / 2);
        int panelTop = Math.max(0, (screenHeight - panelHeight) / 2);

        int contentLeft = panelLeft + MARGIN;
        int y = panelTop + MARGIN;

        final int chestLeft;
        final int chestTop;
        final int titleY;
        final int weightY;
        final BankGridGeometry bankGrid;

        if (chestVisible) {
            chestLeft = contentLeft;
            chestTop = y;
            // Bottom-anchored in the lid, just above the opening -- on the dark inner-lid panel.
            weightY = chestTop + lidHeight - textRow - 4;
            titleY = weightY - textRow;
            int gridLeft = chestLeft + ((CHEST_WIDTH - GRID_WIDTH) / 2);
            bankGrid = BankGridGeometry.of(gridLeft, chestTop + lidHeight + CHEST_INTERIOR_PAD_TOP, COLUMNS, bankRows);
            y = chestTop + lidHeight + interiorHeight + baseHeight + SECTION_GAP;
        } else {
            chestLeft = 0;
            chestTop = 0;
            titleY = y;
            y += textRow;
            weightY = y;
            y += textRow + SECTION_GAP;
            bankGrid = BankGridGeometry.of(contentLeft, y, COLUMNS, bankRows);
            y = bankGrid.bottom() + SECTION_GAP;
        }

        int inventoryLabelY = y;
        y += textRow;

        // The pack aligns under the bank grid, not the chest edge -- nine columns over nine columns.
        BankGridGeometry inventoryGrid = BankGridGeometry.of(bankGrid.left(), y, COLUMNS, INVENTORY_ROWS);
        y = inventoryGrid.bottom() + HOTBAR_GAP;

        BankGridGeometry hotbar = BankGridGeometry.of(bankGrid.left(), y, COLUMNS, HOTBAR_ROWS);
        y = hotbar.bottom();

        final int amountBoxX;
        final int amountBoxY;
        final int amountBoxWidth;
        final int denominationY;
        final int denominationWidth;
        final int actionX;
        final int backY;
        final int actionWidth;

        if (compact) {
            // Two rows under the hotbar, spanning the full content width.
            y += SECTION_GAP;
            amountBoxX = contentLeft;
            amountBoxY = y;
            amountBoxWidth = contentWidth / 4;
            denominationY = y;
            denominationWidth = ((contentWidth - amountBoxWidth) - (ROW_GAP * 3)) / 3;
            y += ROW_HEIGHT + ROW_GAP;
            actionX = contentLeft;
            backY = y;
            // Back alone on its row since the Withdraw button retired (drag-to-withdraw owner
            // decision) -- the drag IS the withdrawal, so the one remaining action gets the
            // full content width.
            actionWidth = contentWidth;
            y += ROW_HEIGHT;
        } else {
            // A column beside the chest, top-aligned with the bank grid. Every control is a
            // full-width row, the denominations included (see CONTROL_COLUMN_WIDTH's docs).
            int columnX = contentLeft + contentWidth + SECTION_GAP;
            amountBoxX = columnX;
            amountBoxY = bankGrid.top();
            amountBoxWidth = CONTROL_COLUMN_WIDTH;
            denominationY = amountBoxY + ROW_HEIGHT + ROW_GAP;
            denominationWidth = CONTROL_COLUMN_WIDTH;
            actionX = columnX;
            backY = denominationY + ((ROW_HEIGHT + ROW_GAP) * 3);
            actionWidth = CONTROL_COLUMN_WIDTH;
        }

        int statusY = y + SECTION_GAP;
        int statusMaxWidth = panelWidth - (MARGIN * 2);

        return new BankBoxLayout(
                compact, chestVisible, panelLeft, panelTop, panelWidth, panelHeight,
                titleY, weightY,
                chestLeft, chestTop, chestVisible ? CHEST_WIDTH : 0, lidHeight, interiorHeight, baseHeight,
                bankGrid, inventoryLabelY, inventoryGrid, hotbar,
                amountBoxX, amountBoxY, amountBoxWidth,
                denominationY, denominationWidth,
                actionX, backY, actionWidth,
                statusY, statusMaxWidth
        );
    }

    public int contentLeft() {
        return panelLeft + MARGIN;
    }

    /** Bottom edge of the chest artwork -- lid, interior and base together. */
    public int chestBottom() {
        return chestTop + chestLidHeight + chestInteriorHeight + chestBaseHeight;
    }

    public int chestRight() {
        return chestLeft + chestWidth;
    }

    /** Left edge of denomination button {@code index}, 0..2 -- side by side when compact. */
    public int denominationX(int index) {
        if (compact) {
            return amountBoxX + amountBoxWidth + ROW_GAP + (index * (denominationWidth + ROW_GAP));
        }
        return actionX;
    }

    /** Top edge of denomination button {@code index}, 0..2 -- stacked when wide. */
    public int denominationY(int index) {
        return compact ? denominationY : denominationY + (index * (ROW_HEIGHT + ROW_GAP));
    }

    /** The one remaining action's left edge -- Back, full-width in both arrangements. */
    public int backX() {
        return actionX;
    }

    public int panelRight() {
        return panelLeft + panelWidth;
    }

    public int panelBottom() {
        return panelTop + panelHeight;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
