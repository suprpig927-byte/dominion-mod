package com.dominionmod.dominion.build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The premade buildings and walls builders can raise. Every building can be built in any tier
 * you've researched (see {@link Palette}): the same layout comes out in thatch, timber, stone, brick or royal materials.
 *
 * <p>Layouts are layers from the bottom up; each layer is rows from north (back) to south (front,
 * where the door is), read west to east. The first {@code sink} layers replace the ground.
 * Characters (tiered materials in capitals):
 * <pre>
 *  ' ' leave alone   '.' clear to air
 *  C floor/foundation   M lower wall course   W wall   L corner post   P trim/gable   G window
 *  &gt; &lt; roof slopes      r roof ridge          F fence  l lantern (floor)  n lantern (hanging)
 *  D door (lower; u = upper)   h/b bed head/foot (kingdom color)   k carpet (Brick+)   p potted flower (Timber+)
 *  K chest  R barrel  Q crafting table  A ladder  B bookshelf  E lectern  Y anvil  U blast furnace  S smithing table
 *  H Research Table  J Training Desk  T Market  N kingdom flag  X/Z wool in primary/secondary color
 *  f farmland  ~ water  y wheat  g fence gate  O spruce log  o spruce fence  s oak slab  c campfire
 *  decorations (free): 1-4 open shutter facing N/E/S/W   v flowering planter   d path   e chimney fire
 * </pre>
 */
public final class Structures {
    private Structures() {}

    public enum Kind { BUILDING, WALL }

    /**
     * @param requires research needed before it can be built (null = none)
     * @param minTier  lowest tier it can be built in
     * @param upgradable whether it gets an upgrade sign
     */
    public record Structure(String id, String name, String description, Kind kind, int sink, List<String[]> layers,
                            @Nullable String requires, int minTier, boolean upgradable) {
        public int width() { return kind == Kind.WALL ? 1 : layers.get(0)[0].length(); }
        public int depth() { return kind == Kind.WALL ? 1 : layers.get(0).length; }
        public int height() { return layers.size(); }
    }

    private static final Map<String, Structure> ALL = new LinkedHashMap<>();

    private static void add(String id, String name, String desc, int sink, @Nullable String requires, int minTier, boolean upgradable,
                            String[][] layers) {
        ALL.put(id, new Structure(id, name, desc, Kind.BUILDING, sink, List.of(layers), requires, minTier, upgradable));
    }

    /** A wall is one column, repeated along the line you draw. Odd and even columns can differ (battlements). */
    private static void wall(String id, String name, String desc, @Nullable String requires, int minTier, String even, String odd) {
        String[][] layers = new String[even.length()][];
        for (int y = 0; y < even.length(); y++) layers[y] = new String[]{"" + even.charAt(y) + odd.charAt(y)};
        ALL.put(id, new Structure(id, name, desc, Kind.WALL, 0, List.of(layers), requires, minTier, false));
    }

    /**
     * Pads the walls with a one-block border and puts a gable roof on top: slopes on the east and west
     * sides with eaves hanging one block past the walls, gables front and back, and a ridge running front to back.
     * {@code extra} layers (already padded) go on top of the roof.
     */
    private static String[][] house(String[][] walls, String[]... extra) {
        int w = walls[0][0].length(), d = walls[0].length;
        int W = w + 2, D = d + 2;
        List<String[]> out = new ArrayList<>();
        String blank = " ".repeat(W);
        for (String[] layer : walls) {
            String[] rows = new String[D];
            rows[0] = blank;
            rows[D - 1] = blank;
            for (int z = 0; z < d; z++) rows[z + 1] = " " + layer[z] + " ";
            out.add(rows);
        }
        for (int k = 0; k <= W / 2; k++) {
            String[] rows = new String[D];
            for (int z = 0; z < D; z++) {
                StringBuilder row = new StringBuilder();
                boolean gable = z == 1 || z == D - 2;
                boolean inside = z > 0 && z < D - 1;
                for (int x = 0; x < W; x++) {
                    char c = ' ';
                    if (x == k && x == W - 1 - k) c = 'r';
                    else if (x == k) c = '>';
                    else if (x == W - 1 - k) c = '<';
                    else if (x > k && x < W - 1 - k && inside) c = gable ? 'P' : (k == 0 ? '.' : '.');
                    row.append(c);
                }
                rows[z] = row.toString();
            }
            out.add(rows);
        }
        for (String[] e : extra) out.add(e);
        return decorate(out, walls.length, W, D);
    }

    /**
     * Dresses a house up: open shutters beside every window, flowering planters under the ground-floor
     * windows, a path and lanterns at the front door, and a smoking chimney up the back gable.
     * (Decorations cost nothing: builders bring their own trim.)
     */
    private static String[][] decorate(List<String[]> layers, int wallLayers, int W, int D) {
        char[][][] g = new char[layers.size()][][];
        for (int y = 0; y < g.length; y++) {
            g[y] = new char[D][];
            for (int z = 0; z < D; z++) g[y][z] = layers.get(y)[z].toCharArray();
        }
        for (int y = 1; y < wallLayers; y++) {
            for (int z = 1; z < D - 1; z++) {
                for (int x = 1; x < W - 1; x++) {
                    char c = g[y][z][x];
                    int dx = x == 1 ? -1 : x == W - 2 ? 1 : 0, dz = z == 1 ? -1 : z == D - 2 ? 1 : 0;
                    if ((dx == 0) == (dz == 0)) continue; // not on a wall, or a corner
                    char shutter = dz < 0 ? '1' : dx > 0 ? '2' : dz > 0 ? '3' : '4';
                    if (c == 'G') {
                        for (int t = -1; t <= 1; t += 2) {
                            int wx = x + (dz != 0 ? t : 0), wz = z + (dx != 0 ? t : 0);
                            char wall = g[y][wz][wx];
                            if ((wall == 'W' || wall == 'L' || wall == 'M') && g[y][wz + dz][wx + dx] == ' ') g[y][wz + dz][wx + dx] = shutter;
                        }
                        if (y == 2 && g[1][z + dz][x + dx] == ' ') g[1][z + dz][x + dx] = 'v';
                    } else if (c == 'D' && dz > 0) {
                        if (g[0][z + 1][x] == ' ') g[0][z + 1][x] = 'd';
                        for (int t = -2; t <= 2; t += 4) {
                            if (x + t > 0 && x + t < W - 1 && g[y][z + 1][x + t] == ' ') g[y][z + 1][x + t] = 'l';
                        }
                    }
                }
            }
        }
        // the chimney: up the back, beside the east slope, poking above the roof with a fire on top
        if (W >= 9 && g.length >= wallLayers + 5) {
            int xc = W - 3;
            for (int y = 1; y <= wallLayers + 3; y++) g[y][0][xc] = 'C';
            g[wallLayers + 4][0][xc] = 'e';
        }
        String[][] out = new String[g.length][];
        for (int y = 0; y < g.length; y++) {
            out[y] = new String[D];
            for (int z = 0; z < D; z++) out[y][z] = new String(g[y][z]);
        }
        return out;
    }

    /** A padded layer that's empty except one character in the middle (for a flag on the ridge). */
    private static String[] ridgeFlag(String[][] walls) {
        int W = walls[0][0].length() + 2, D = walls[0].length + 2;
        String[] rows = new String[D];
        for (int z = 0; z < D; z++) {
            char[] row = " ".repeat(W).toCharArray();
            if (z == D / 2) row[W / 2] = 'N';
            rows[z] = new String(row);
        }
        return rows;
    }

    private static final String[][] COTTAGE = {
            {"CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC"},
            {"LMMMMML", "MhkkkhM", "MbkkkbM", "Mkklk.M", "MkkkkkM", "MQ.pRKM", "LMMDMML"},
            {"LWWGWWL", "W.....W", "W.....W", "G.....G", "W.....W", "W.....W", "LWGuGWL"},
            {"LWWWWWL", "W.....W", "W..n..W", "W.....W", "W.....W", "W.....W", "LPPPPPL"},
    };

    private static final String[][] BARRACKS = {
            {"CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC"},
            {"LMMMMMMML", "Mh.h.h.hM", "Mb.b.b.bM", "MkkkkkkkM", "M...l...M", "MKR...RKM", "LMMMDMMML"},
            {"LWWGWGWWL", "W.......W", "W.......W", "G.......G", "W.......W", "W.......W", "LWGWuWGWL"},
            {"LWWWWWWWL", "W.......W", "W...n...W", "W.......W", "W.......W", "W.......W", "LPPPPPPPL"},
    };

    private static final String[][] STOREHOUSE = {
            {"CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC"},
            {"LMMMMMMML", "MRKKRKKRM", "M.......M", "MR.....RM", "M...l...M", "MRK...KRM", "LMMMDMMML"},
            {"LWWWWWWWL", "WR..R..RW", "W.......W", "G.......G", "W.......W", "WR.....RW", "LWGWuWGWL"},
            {"LWWWWWWWL", "W.......W", "W.......W", "G...n...G", "W.......W", "W.......W", "LPPPPPPPL"},
    };

    private static final String[][] LIBRARY = {
            {"CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC"},
            {"LMMMMML", "MBBHBBM", "MkkkkkM", "MEkkkEM", "MkkkkkM", "MB.l.BM", "LMMDMML"},
            {"LWWWWWL", "WBB.BBW", "W.....W", "G.....G", "W.....W", "WB...BW", "LWGuGWL"},
            {"LWWWWWL", "W.....W", "W.....W", "G..n..G", "W.....W", "W.....W", "LPPPPPL"},
    };

    private static final String[][] SMITHY = {
            {"CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC"},
            {"LMMMMML", "MU.S.UM", "M.....M", "MR.Y.KM", "M.....M", "MR...RM", "LM...ML"},
            {"LWWWWWL", "W.....W", "G.....G", "W.....W", "G.....G", "W.....W", "LW...WL"},
            {"LWWWWWL", "W.....W", "W..n..W", "W.....W", "W.....W", "W.....W", "LPPPPPL"},
    };

    private static final String[][] UNIVERSITY = {
            {"CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC"},
            {"LMMMMMMMMML", "MBBBBJBBBBM", "MkkkkkkkkkM", "MEkkkkkkkEM", "MkkkklkkkkM", "MEkkkkkkkEM", "MkkkkkkkkkM", "MBR.....RBM", "LMMMMDMMMML"},
            {"LWWGWWWGWWL", "WBBBB.BBBBW", "W.........W", "G.........G", "W.........W", "G.........G", "W.........W", "WB.......BW", "LWWGWuWGWWL"},
            {"LWWWWWWWWWL", "W.........W", "W.........W", "W....n....W", "W.........W", "W....n....W", "W.........W", "W.........W", "LWWWWWWWWWL"},
            {"LWWGWWWGWWL", "W.........W", "W.........W", "G.........G", "W.........W", "G.........G", "W.........W", "W.........W", "LPPPPPPPPPL"},
    };

    static {
        add("cottage", "Cottage", "A family home: two beds, a chest, a crafting table and a pitched roof.", 1, null, 0, true, house(COTTAGE));
        add("barracks", "Barracks", "Bunks for four soldiers and lockers for their gear. Flies your flag from the ridge.", 1, null, 1, true,
                house(BARRACKS, ridgeFlag(BARRACKS)));
        add("storehouse", "Storehouse", "A warehouse lined with chests and barrels. Its chests join your storehouse, wherever you build it.", 1,
                null, 0, true, house(STOREHOUSE));
        add("library", "Library", "Bookshelves, lecterns and a Research Table. Scholars work here to speed up research.", 1, null, 0, true,
                house(LIBRARY));
        add("smithy", "Smithy", "Blast furnaces, an anvil and a smithing table, open at the front. Blacksmiths work here.", 1, "blacksmithing", 1, true,
                house(SMITHY));
        add("university", "University", "A great hall of learning with a Training Desk: turn citizens into Wizards, Barbarians, Monks and Engineers.",
                1, "university", 2, true, house(UNIVERSITY, ridgeFlag(UNIVERSITY)));

        add("farm", "Wheat Farm", "A fenced 7x7 wheat field around a water source. Give it a Farmer.", 1, null, 0, true, new String[][]{
                {"CCCCCCCCC", "CfffffffC", "CfffffffC", "CfffffffC", "Cfff~fffC", "CfffffffC", "CfffffffC", "CfffffffC", "CCCCCCCCC"},
                {"FFFFFFFFF", "FyyyyyyyF", "FyyyyyyyF", "FyyyyyyyF", "Fyyy.yyyF", "FyyyyyyyF", "FyyyyyyyF", "FyyyyyyyF", "FFFFgFFFF"},
                {"l       l", "         ", "         ", "         ", "         ", "         ", "         ", "         ", "l       l"}});

        add("watchtower", "Watchtower", "A tall tower with arrow slits and a lookout platform. Flies your flag.", 1, "stone", 2, true, new String[][]{
                {"MMMMM", "MMMMM", "MMMMM", "MMMMM", "MMMMM"},
                {"LMMML", "M.A.M", "M...M", "M...M", "LMDML"},
                {"LMMML", "M.A.M", "M...M", "M...M", "LMuML"},
                {"LWWWL", "W.A.W", "W...W", "W...W", "LWWWL"},
                {"LW.WL", "W.A.W", ".....", "W...W", "LW.WL"},
                {"LWWWL", "W.A.W", "W...W", "W...W", "LWWWL"},
                {"LWWWL", "W.A.W", "W...W", "W...W", "LWWWL"},
                {"PPPPP", "PPAPP", "PPPPP", "PPPPP", "PPPPP"},
                {"F.F.F", ".....", "F.N.F", ".....", "F.F.F"},
                {"l...l", "     ", "     ", "     ", "l...l"}});

        add("market_stall", "Market Stall", "A Market under an awning striped in your colors.", 0, null, 0, false, new String[][]{
                {"F...F", ".....", "..T..", ".....", "F...F"},
                {"F...F", ".....", ".....", ".....", "F...F"},
                {"XZXZX", "XZXZX", "XZXZX", "XZXZX", "XZXZX"}});

        add("wall_gate", "Wall Gate", "A stone gateway with fence gates, to set into your walls.", 0, "stone", 2, false, new String[][]{
                {"LgggL"}, {"M...M"}, {"M...M"}, {"MMMMM"}, {"M M M"}});

        wall("stone_wall", "Stone Wall", "A wall 4 high with battlements, in your tier's stone. Click two points to draw it.", "stone", 2, "MMMMM", "MMMM ");
        wall("palisade", "Palisade", "A sharpened spruce log fence, 4 high. Click two points to draw it.", null, 0, "OOOOo", "OOOOo");
    }

    public static @Nullable Structure get(String id) {
        return ALL.get(id);
    }

    public static java.util.Collection<Structure> all() {
        return ALL.values();
    }
}
