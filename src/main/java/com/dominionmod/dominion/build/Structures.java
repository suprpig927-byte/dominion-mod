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
 *  D door (lower; u = upper)   h/b bed head/foot (kingdom color)   k carpet (Brick+)   0 rug (secondary color, Timber+)
 *  furniture: t table   5-8 chair facing N/E/S/W   m stove (smoker)   V wash basin   j wall shelf (on the north wall)
 *  on tables only (never the floor): p potted flower   i candles   a lantern   I decorated pot
 *  9 : ; ladders hanging on the south/west/east wall (A hangs on the north wall)
 *  K chest  R barrel  Q crafting table  A ladder  B bookshelf  E lectern  Y anvil  U blast furnace  S smithing table
 *  H Research Table  J Training Desk  T Market  N kingdom flag  X/Z wool in primary/secondary color
 *  f farmland  ~ water  y wheat  g fence gate  O spruce log  o spruce fence  s oak slab  c campfire
 *  x/z log beam running east-west/north-south
 *  decorations (free): 1-4 open shutter facing N/E/S/W   v flowering planter   d path   e chimney fire
 *  [ ] eave bracket on the west/east side   w door awning
 *  orc villages: % hide (brown wool)  # coarse dirt  @ skull  = hay bale  ! bone block  &amp; the War Totem
 * </pre>
 */
public final class Structures {
    private Structures() {}

    public enum Kind { BUILDING, WALL }

    /** Roads are drawn like walls, but lie flat in the ground, three blocks wide. */
    public static boolean isRoad(Structure s) {
        return s.id().equals("road");
    }

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
    /** Structures that exist only as upgrades or in the wild (second storeys, orc huts): never on the build list. */
    private static final Map<String, Structure> HIDDEN = new LinkedHashMap<>();

    private static void hidden(String id, String name, String desc, int sink, @Nullable String requires, int minTier, boolean upgradable,
                               String[][] layers) {
        layers = backLadders(layers);
        HIDDEN.put(id, new Structure(id, name, desc, Kind.BUILDING, sink, List.of(layers), requires, minTier, upgradable));
    }

    /**
     * Makes sure every ladder hangs on a solid wall: a window or opening right behind a ladder is
     * walled up (a ladder can't hang on glass, and glass behind a ladder looks wrong anyway).
     * 'A' hangs on the block to its north, '9' south, ':' west, ';' east.
     */
    private static String[][] backLadders(String[][] layers) {
        String[][] out = new String[layers.length][];
        for (int y = 0; y < layers.length; y++) out[y] = layers[y].clone();
        for (int y = 0; y < out.length; y++) {
            for (int z = 0; z < out[y].length; z++) {
                for (int x = 0; x < out[y][z].length(); x++) {
                    char c = out[y][z].charAt(x);
                    int bx = x, bz = z;
                    switch (c) {
                        case 'A' -> bz = z - 1;
                        case '9' -> bz = z + 1;
                        case ':' -> bx = x - 1;
                        case ';' -> bx = x + 1;
                        default -> { continue; }
                    }
                    if (bz < 0 || bz >= out[y].length || bx < 0 || bx >= out[y][bz].length()) continue;
                    char behind = out[y][bz].charAt(bx);
                    if ("G. nlpjiau|γ".indexOf(behind) >= 0) {
                        char[] row = out[y][bz].toCharArray();
                        row[bx] = y <= 1 ? 'M' : 'W';
                        out[y][bz] = new String(row);
                    }
                }
            }
        }
        return out;
    }

    /** The second-storey version of a house design, if it has one. */
    public static @Nullable Structure storeyOf(String id) {
        return id.endsWith("_2") ? null : HIDDEN.get(id + "_2");
    }

    /** All the house designs a family can live in (for the wild towns). */
    public static final List<String> HOMES = List.of("cottage", "longhouse", "farmhouse", "townhouse");

    private static void add(String id, String name, String desc, int sink, @Nullable String requires, int minTier, boolean upgradable,
                            String[][] layers) {
        layers = backLadders(layers);
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
     * windows, a path, lanterns and an awning at the front door, a log beam round the top of the walls,
     * brackets under the eaves, a window in each gable, and a smoking chimney up the back gable.
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
        // timber framing: a log beam runs round the top of the walls
        int top = wallLayers - 1;
        for (int z = 1; z < D - 1; z++) {
            for (int x = 1; x < W - 1; x++) {
                boolean side = x == 1 || x == W - 2, end = z == 1 || z == D - 2;
                if (side == end) continue; // inside, or a corner post
                char c = g[top][z][x];
                if (c == 'W' || c == 'P') g[top][z][x] = end ? 'x' : 'z';
            }
        }
        // carved brackets under the eaves along both sides
        for (int z = 1; z < D - 1; z++) {
            if (g[top][z][0] == ' ') g[top][z][0] = '[';
            if (g[top][z][W - 1] == ' ') g[top][z][W - 1] = ']';
        }
        // a little window high in each gable
        int gy = wallLayers + 1;
        if (W - 4 >= 3 && gy < g.length) {
            for (int z : new int[]{1, D - 2}) if (g[gy][z][W / 2] == 'P') g[gy][z][W / 2] = 'G';
        }
        // a sloped awning over the front door
        for (int x = 1; x < W - 1; x++) {
            if (g[1][D - 2][x] != 'D') continue;
            for (int t = -1; t <= 1; t++) if (g[3][D - 1][x + t] == ' ') g[3][D - 1][x + t] = 'w';
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
            {"LMMMMML", "Mh.m.hM", "Mb...bM", "M.8tt6M", "M.000.M", "MQR.IKM", "LMMDMML"},
            {"LWWGWWL", "W.j.j.W", "W.....W", "G..pi.G", "W.....W", "W.....W", "LWGuGWL"},
            {"LWWWWWL", "W.....W", "W..n..W", "W.....W", "W.....W", "W.....W", "LPPPPPL"},
    };

    private static final String[][] BARRACKS = {
            {"CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC"},
            {"LMMMMMMML", "Mh.h.h.hM", "Mb.b.b.bM", "MkkkkkkkM", "M8tt6..VM", "MKR...RKM", "LMMMDMMML"},
            {"LWWGWGWWL", "W.j.j.j.W", "W.......W", "G.......G", "W.ia....W", "W.......W", "LWGWuWGWL"},
            {"LWWWWWWWL", "W.......W", "W...n...W", "W.......W", "W.......W", "W.......W", "LPPPPPPPL"},
    };

    private static final String[][] STOREHOUSE = {
            {"CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC"},
            {"LMMMMMMML", "MRKKRKKRM", "M.......M", "MR.....RM", "M..8t...M", "MRK...KRM", "LMMMDMMML"},
            {"LWWWWWWWL", "WR..R..RW", "W.......W", "G.......G", "W...a...W", "WR.....RW", "LWGWuWGWL"},
            {"LWWWWWWWL", "W.......W", "W.......W", "G...n...G", "W.......W", "W.......W", "LPPPPPPPL"},
    };

    private static final String[][] LIBRARY = {
            {"CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC"},
            {"LMMMMML", "MBBHBBM", "MkkkkkM", "ME8t6EM", "MkkkkkM", "MB...BM", "LMMDMML"},
            {"LWWWWWL", "WBB.BBW", "W.....W", "G..i..G", "W.....W", "WB...BW", "LWGuGWL"},
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
            {"LMMMMMMMMML", "MBBBBJBBBBM", "MkkkkkkkkkM", "MEkkkkkkkEM", "Mkkk8t6kkkM", "MEkkkkkkkEM", "MkkkkkkkkkM", "MBR.....RBM", "LMMMMDMMMML"},
            {"LWWGWWWGWWL", "WBBBB.BBBBW", "W.........W", "G.........G", "W....i....W", "G.........G", "W.........W", "WB.......BW", "LWWGWuWGWWL"},
            {"LWWWWWWWWWL", "W.........W", "W.........W", "W....n....W", "W.........W", "W....n....W", "W.........W", "W.........W", "LWWWWWWWWWL"},
            {"LWWGWWWGWWL", "W.........W", "W.........W", "G.........G", "W.........W", "G.........G", "W.........W", "W.........W", "LPPPPPPPPPL"},
    };

    private static final String[][] LONGHOUSE = {
            {"CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC"},
            {"LMMMMML", "MhkhkhM", "MbkbkbM", "M.....M", "MK.5.KM", "M.8t6.M", "MR8t6RM", "M..7..M", "MQ.0.mM", "MV.0.IM", "LMMDMML"},
            {"LWWGWWL", "W.j.j.W", "W.....W", "G.....G", "W.....W", "W..p..W", "W..i..W", "G.....G", "W.....W", "W.....W", "LWGuGWL"},
            {"LWWWWWL", "W.....W", "W.....W", "W.....W", "W..n..W", "W.....W", "W..n..W", "W.....W", "W.....W", "W.....W", "LPPPPPL"},
    };

    /** A wide farmhouse with a covered porch along the front. */
    private static final String[][] FARMHOUSE = {
            {"CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC"},
            {"LMMMMMMML", "Mh.h.KRmM", "Mb.b....M", "Mkkk5kkkM", "MQ.8t6.VM", "MI.....RM", "LMMMDMMML", "O.......O"},
            {"LWWGWGWWL", "W.j.j...W", "W.......W", "G.......G", "W...p...W", "W.......W", "LWGWuWGWL", "O.n...n.O"},
            {"LWWWWWWWL", "W.......W", "W...n...W", "W.......W", "W...n...W", "W.......W", "LPPPPPPPL", "OxxxxxxxO"},
    };

    /** A narrow town house with a tall roof. */
    private static final String[][] TOWNHOUSE = {
            {"CCCCC", "CCCCC", "CCCCC", "CCCCC", "CCCCC", "CCCCC", "CCCCC"},
            {"LMMML", "MhkhM", "MbkbM", "M8t6M", "MK.QM", "MR.VM", "LMDML"},
            {"LWGWL", "W...W", "G...G", "W.p.W", "G...G", "W...W", "LGuGL"},
            {"LWWWL", "W...W", "W.n.W", "W...W", "W...W", "W...W", "LPPPL"},
    };

    /**
     * Stacks a second storey on a one-storey design: a beamed floor with a ladder up from the
     * back wall, an upstairs with two more beds and a chest, and the same windows as downstairs.
     */
    private static String[][] twoStory(String[][] walls) {
        int w = walls[0][0].length(), d = walls[0].length;
        char[][] l1 = grid(walls[1]), l2 = grid(walls[2]), l3 = grid(walls[3]);
        // the ladder: in the back row, as near the middle as there's room
        int lx = w / 2;
        for (int off = 0; off < w; off++) {
            int a = w / 2 - off, b = w / 2 + off;
            if (a > 0 && ".k".indexOf(l1[1][a]) >= 0) { lx = a; break; }
            if (b < w - 1 && ".k".indexOf(l1[1][b]) >= 0) { lx = b; break; }
        }
        l1[1][lx] = 'A';
        l2[1][lx] = 'A';
        l3[1][lx] = 'A';
        char[][] floor = new char[d][w], u1 = new char[d][w], u2 = new char[d][w], u3 = new char[d][w];
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                boolean edgeX = x == 0 || x == w - 1, edgeZ = z == 0 || z == d - 1;
                char wall3 = l3[z][x];
                if (edgeX && edgeZ) {
                    floor[z][x] = u1[z][x] = u2[z][x] = u3[z][x] = wall3 == 'O' ? 'O' : 'L';
                } else if (edgeX || edgeZ) {
                    floor[z][x] = edgeZ ? 'x' : 'z';
                    u1[z][x] = wall3 == 'x' || wall3 == 'P' ? 'W' : wall3;
                    char win = l2[z][x];
                    u2[z][x] = win == 'u' ? 'G' : (win == 'O' || win == 'n' ? wall3 : win);
                    u3[z][x] = wall3;
                } else {
                    floor[z][x] = 'P';
                    u1[z][x] = u2[z][x] = u3[z][x] = '.';
                }
                // a porch row stays open (posts at the ends)
                if (l1[z][x] == 'O' || z == d - 1 && l1[z][0] == 'O') {
                    if (!(edgeX && edgeZ)) {
                        u1[z][x] = u2[z][x] = u3[z][x] = ' ';
                        floor[z][x] = l3[z][x] == 'x' ? 'x' : floor[z][x];
                    }
                }
            }
        }
        boolean porch = l1[d - 1][0] == 'O';
        if (porch) {
            for (int x = 0; x < w; x++) { u1[d - 1][x] = u2[d - 1][x] = u3[d - 1][x] = '.'; floor[d - 1][x] = 'x'; }
            floor[d - 1][0] = floor[d - 1][w - 1] = 'O';
            // the upstairs front wall is the row behind the porch
            int fz = d - 2;
            for (int x = 1; x < w - 1; x++) {
                u1[fz][x] = 'W';
                u2[fz][x] = l2[fz][x] == 'u' ? 'G' : l2[fz][x];
                u3[fz][x] = 'P';
            }
            u1[fz][0] = u1[fz][w - 1] = u2[fz][0] = u2[fz][w - 1] = u3[fz][0] = u3[fz][w - 1] = 'L';
        }
        floor[1][lx] = 'A';
        // upstairs furniture: two beds, a chest and a lantern
        u1[2][1] = 'h';
        u1[3][1] = 'b';
        if (w >= 5) {
            u1[2][w - 2] = 'h';
            u1[3][w - 2] = 'b';
        }
        int cz = porch ? d - 3 : d - 2;
        if (u1[cz][w - 2] == '.') u1[cz][w - 2] = 'K';
        u3[d / 2][w / 2] = 'n';
        return new String[][]{walls[0], rows(l1), rows(l2), rows(l3), rows(floor), rows(u1), rows(u2), rows(u3)};
    }

    private static char[][] grid(String[] rows) {
        char[][] g = new char[rows.length][];
        for (int i = 0; i < rows.length; i++) g[i] = rows[i].toCharArray();
        return g;
    }

    private static String[] rows(char[][] g) {
        String[] out = new String[g.length];
        for (int i = 0; i < g.length; i++) out[i] = new String(g[i]);
        return out;
    }

    /** A two-storey house, plus clearing for the old roof so it can be built over a one-storey one. */
    private static String[][] storeyHouse(String[][] walls) {
        String[][] h = house(twoStory(walls));
        int W = h[0][0].length();
        for (int y = 4; y < Math.min(h.length, 5 + W / 2); y++) {
            for (int z = 0; z < h[y].length; z++) {
                char[] row = h[y][z].toCharArray();
                boolean border = z == 0 || z == h[y].length - 1;
                for (int x = 0; x < row.length; x++) if (row[x] == ' ' && (border || x == 0 || x == row.length - 1)) row[x] = '.';
                h[y][z] = new String(row);
            }
        }
        return h;
    }

    /**
     * A castle: a curtain wall with four corner towers and a gatehouse, round a courtyard with a
     * keep at the back. The keep has a great hall below, a barracks above, and the flag on its roof.
     */
    private static String[][] castle() {
        final int S = 23, H = 13;
        char[][][] g = new char[H][S][S];
        for (char[][] layer : g) for (char[] row : layer) java.util.Arrays.fill(row, ' ');
        for (int z = 0; z < S; z++) for (int x = 0; x < S; x++) g[0][z][x] = 'C';
        int k0x = 7, k1x = 15, k0z = 3, k1z = 11; // the keep
        for (int y = 1; y < H; y++) {
            for (int z = 0; z < S; z++) {
                for (int x = 0; x < S; x++) {
                    boolean curtain = x == 0 || x == S - 1 || z == 0 || z == S - 1;
                    boolean inTower = (x < 5 || x > S - 6) && (z < 5 || z > S - 6);
                    boolean towerEdge = inTower && (x == 0 || x == 4 || x == S - 5 || x == S - 1 || z == 0 || z == 4 || z == S - 5 || z == S - 1);
                    boolean keep = x >= k0x && x <= k1x && z >= k0z && z <= k1z;
                    boolean keepEdge = keep && (x == k0x || x == k1x || z == k0z || z == k1z);
                    char c = g[y][z][x];
                    if (towerEdge) {
                        if (y <= 8) c = y <= 2 ? 'M' : 'W';
                        if (y == 9) c = (x + z) % 2 == 0 ? 'W' : ' ';
                        boolean corner = (x == 0 || x == 4 || x == S - 5 || x == S - 1) && (z == 0 || z == 4 || z == S - 5 || z == S - 1);
                        if (corner && y <= 8) c = 'L';
                        if (y == 4 && !corner && (x == 2 || x == S - 3 || z == 2 || z == S - 3) && (x == 0 || x == S - 1 || z == 0 || z == S - 1)) c = 'G';
                    } else if (inTower) {
                        if (y == 8) c = 'P';
                        else if (y < 8) c = '.';
                    } else if (curtain) {
                        if (y <= 5) c = y <= 2 ? 'M' : 'W';
                        if (y == 6) c = (x + z) % 2 == 0 ? 'W' : ' ';
                    } else if (keepEdge) {
                        boolean corner = (x == k0x || x == k1x) && (z == k0z || z == k1z);
                        if (y <= 10) c = corner ? 'L' : (y <= 1 ? 'M' : 'W');
                        if (y == 11) c = (x + z) % 2 == 0 ? 'W' : ' ';
                        if (!corner && (y == 3 || y == 7) && ((x - k0x) % 3 == 1 && (z == k0z || z == k1z) || (z - k0z) % 3 == 1 && (x == k0x || x == k1x))) c = 'G';
                    } else if (keep) {
                        if (y == 5 || y == 10) c = 'P';
                        else if (y < 10) c = '.';
                    } else if (y <= 4) {
                        c = '.'; // the courtyard
                    }
                    g[y][z][x] = c;
                }
            }
        }
        // towers: ladders up to their roofs, doors into the courtyard
        int[][] towers = {{2, 4, 3}, {S - 3, 4, 3}, {2, S - 5, S - 4}, {S - 3, S - 5, S - 4}};
        for (int[] t : towers) {
            int tx = t[0];
            boolean north = t[1] == 4;
            int doorZ = north ? 4 : S - 5;
            int doorX = north ? tx : (tx == 2 ? 3 : S - 4);
            g[1][doorZ][doorX] = '.';
            g[2][doorZ][doorX] = '.';
            int lz = north ? 1 : S - 4;
            for (int y = 1; y <= 8; y++) g[y][lz][tx] = 'A';
        }
        // ladders up the inside of the curtain walls to the wall-walk (beside a gap in the battlements)
        for (int y = 1; y <= 5; y++) {
            g[y][1][11] = 'A';
            g[y][S - 2][7] = '9';
            g[y][S - 2][15] = '9';
            g[y][11][1] = ':';
            g[y][11][S - 2] = ';';
        }
        // the gatehouse: an opening in the front wall with a portcullis of fences above
        for (int x = 10; x <= 12; x++) {
            for (int y = 1; y <= 3; y++) g[y][S - 1][x] = '.';
            g[4][S - 1][x] = 'o';
            g[0][S - 1][x] = 'd';
        }
        for (int y = 1; y <= 7; y++) { g[y][S - 1][9] = 'L'; g[y][S - 1][13] = 'L'; }
        // a path from the gate to the keep door, and lanterns along it
        for (int z = k1z + 1; z < S - 1; z++) g[0][z][11] = 'd';
        for (int z = k1z + 2; z < S - 2; z += 3) { g[1][z][9] = 'l'; g[1][z][13] = 'l'; }
        // the keep: door, ladder, furniture
        g[1][k1z][11] = 'D';
        g[2][k1z][11] = 'u';
        for (int y = 1; y <= 10; y++) g[y][k0z + 1][k0x + 1] = 'A';
        g[5][k0z + 1][k0x + 1] = 'A';
        g[10][k0z + 1][k0x + 1] = 'A';
        char[][] hall = g[1];
        hall[k0z + 1][11] = 'N';
        hall[k0z + 1][k0x + 3] = 'K';
        hall[k0z + 1][k1x - 1] = 'K';
        hall[k0z + 1][k1x - 2] = 'R';
        hall[k0z + 4][k0x + 1] = 'Q';
        hall[k0z + 4][k1x - 1] = 'Y';
        hall[k0z + 4][10] = '8';
        hall[k0z + 4][11] = 't';
        hall[k0z + 4][12] = '6';
        g[2][k0z + 4][11] = 'i';
        for (int x = k0x + 3; x <= k1x - 3; x++) hall[k0z + 3][x] = 'k';
        char[][] up = g[6];
        for (int i = 0; i < 3; i++) {
            up[k0z + 2][k0x + 3 + i * 2] = 'h';
            up[k0z + 3][k0x + 3 + i * 2] = 'b';
            up[k0z + 5][k0x + 3 + i * 2] = 'h';
            up[k0z + 6][k0x + 3 + i * 2] = 'b';
        }
        up[k1z - 1][k1x - 1] = 'K';
        g[9][(k0z + k1z) / 2][11] = 'n';
        g[4][(k0z + k1z) / 2][11] = 'n';
        // the flag flies from the roof of the keep
        g[11][(k0z + k1z) / 2][11] = 'N';
        // courtyard: a well and some barrels
        g[1][16][4] = 'R'; g[1][16][5] = 'R'; g[1][17][4] = 'K';
        g[1][16][S - 6] = 'c';
        String[][] out = new String[H][];
        for (int y = 0; y < H; y++) out[y] = rows(g[y]);
        return out;
    }

    /** An orc hut: hide walls on log posts, a peaked hide roof, a straw bed and a loot chest. */
    private static final String[][] ORC_HUT = {
            {"#####", "#####", "#####", "#####", "#####"},
            {"O%%%O", "%=.K%", "%...%", "%...%", "O%.%O"},
            {"O%%%O", "%...%", "%...%", "%...%", "O%.%O"},
            {" %%% ", "%...%", "%...%", "%...%", " %%% "},
            {"     ", " %%% ", " %O% ", " %%% ", "     "},
            {"     ", "     ", "  @  ", "     ", "     "},
    };

    /** The Stables: three stalls with hay racks, a water trough, a tack chest and wide gates for horses. */
    private static final String[][] STABLES = {
            {"CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC"},
            {"LMMMMMMMMML", "MV.=F.=F.=M", "M...F..F..M", "M...F..F..M", "M.........M", "MRK.....RRM", "LMMMgggMMML"},
            {"LWWGWWWGWWL", "W.........W", "W.........W", "G.........G", "W.........W", "W.........W", "LWWW...WWWL"},
            {"LWWWWWWWWWL", "W.........W", "W....n....W", "W.........W", "W....n....W", "W.........W", "LPPPPPPPPPL"},
    };

    /** The Warlord's great hut: bone pillars, a hay throne flanked by skulls, and a roof of hides. */
    private static final String[][] ORC_WARLORD_HUT = {
            {"#######", "#######", "#######", "#######", "#######", "#######", "#######"},
            {"O%%%%%O", "%!.=.!%", "%.....%", "%.....%", "%.....%", "%K...K%", "O%%.%%O"},
            {"O%%%%%O", "%@...@%", "%.....%", "%.....%", "%.....%", "%.....%", "O%%.%%O"},
            {"O%%%%%O", "%.....%", "%.....%", "%.....%", "%.....%", "%.....%", "O%%%%%O"},
            {"       ", " %%%%% ", " %...% ", " %...% ", " %...% ", " %%%%% ", "       "},
            {"       ", "       ", "  %%%  ", "  %O%  ", "  %%%  ", "       ", "       "},
            {"       ", "       ", "       ", "   @   ", "       ", "       ", "       "},
    };

    /** The war totem: a skull-topped pole and a bonfire in the middle of an orc village. */
    private static final String[][] ORC_TOTEM = {
            {"#####", "#####", "#####", "#####", "#####"},
            {"!...!", ".....", "..O..", ".....", "!.c.!"},
            {"@...@", ".....", "..O..", ".....", "....."},
            {".....", ".....", "..O..", ".....", "....."},
            {".....", ".....", "..&..", ".....", "....."},
    };

    // ------------------------------------------------------------------ workshops and civic buildings

    private static final String[][] TAVERN = {
            {"CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC"},
            {"LMMMMMMMMML", "MR,RR.mKQRM", "Mtttt.....M", "M.........M", "M6t8...6t8M", "M.........M", "M6t8...6t8M", "M....0....M", "LMMMMDMMMML"},
            {"LWWGWWWGWWL", "WR..R....RW", "W.i.a.....W", "G.........G", "W.p.....i.W", "W.........W", "W.a.....p.W", "W.........W", "LWWGWuWGWWL"},
            {"LWWWWWWWWWL", "W.........W", "W.........W", "W..n...n..W", "W.........W", "W.........W", "W..n...n..W", "W.........W", "LPPPPPPPPPL"},
    };

    private static final String[][] CHAPEL = {
            {"CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC"},
            {"LMMMMMMML", "M..tEt..M", "M...0...M", "M777.777M", "M...0...M", "M777.777M", "M...0...M", "M777.777M", "M...0...M", "MK.....RM", "LMMMDMMML"},
            {"LWWWGWWWL", "W..i.i..W", "W.......W", "G.......G", "W.......W", "G.......G", "W.......W", "G.......G", "W.......W", "W.......W", "LWWWuWWWL"},
            {"LWWWWWWWL", "W.......W", "W.......W", "G.......G", "W.......W", "G.......G", "W.......W", "G.......G", "W.......W", "W.......W", "LWWWGWWWL"},
            {"LWWWWWWWL", "W.......W", "W...n...W", "W.......W", "W.......W", "W...n...W", "W.......W", "W.......W", "W...n...W", "W.......W", "LPPPPPPPL"},
    };

    private static final String[][] BAKERY = {
            {"CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC"},
            {"LMMMMML", "M++m=RM", "M.....M", "Mtt..QM", "M.....M", "MK..RRM", "LMMDMML"},
            {"LWWGWWL", "W.....W", "W.....W", "Gai...G", "W.....W", "W...R.W", "LWWuWWL"},
            {"LWWWWWL", "W.....W", "W.....W", "W..n..W", "W.....W", "W.....W", "LPPPPPL"},
    };

    private static final String[][] MILL = {
            {"CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC"},
            {"LMMMMML", "M)=.=)M", "M.....M", "M=...RM", "M?...RM", "MK...QM", "LMMDMML"},
            {"LWWGWWL", "W.=.=.W", "W.....W", "G.....G", "W.....W", "W.....W", "LWWuWWL"},
            {"LWWWWWL", "W.....W", "W.....W", "W..n..W", "W.....W", "W.....W", "LPPPPPL"},
    };

    private static final String[][] BUTCHER = {
            {"CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC"},
            {"LMMMMMMML", "Mmm.V.RRM", "M.......M", "Mttt....M", "M.......M", "MK.....QM", "LMMMDMMML"},
            {"LWWWGWWWL", "W.......W", "W.......W", "Gpai....G", "W.......W", "W.......W", "LWWWuWWWL"},
            {"LWWWWWWWL", "W.......W", "W...n...W", "W.......W", "W...n...W", "W.......W", "LPPPPPPPL"},
    };

    private static final String[][] MINE = {
            {"CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC"},
            {"LMMMMML", "MRU.URM", "M.....M", "M.///.M", "M.....M", "MK...QM", "LMMDMML"},
            {"LWWGWWL", "W.....W", "W.....W", "G.....G", "W.....W", "W.....W", "LWWuWWL"},
            {"LWWWWWL", "W.....W", "W.....W", "W..n..W", "W.....W", "W.....W", "LPPPPPL"},
    };

    private static final String[][] LUMBER_MILL = {
            {"CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC"},
            {"LMMMMMMML", "MOO...OOM", "M.......M", "M.(.(.(.M", "M.......M", "MK.R.R.QM", "LMMMDMMML"},
            {"LWWWGWWWL", "WOO...OOW", "W.......W", "G.......G", "W.......W", "W.......W", "LWWWuWWWL"},
            {"LWWWWWWWL", "W.......W", "W...n...W", "W.......W", "W...n...W", "W.......W", "LPPPPPPPL"},
    };

    private static final String[][] HARBOR = {
            {"CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CC~~~~~CC", "CC~~~~~CC", "CCCCCCCCC", "CCCCCCCCC"},
            {"LMMMMMMML", "MRR.}.RRM", "M.......M", "M.......M", "M.......M", "MK.....QM", "LMMMDMMML"},
            {"LWWWGWWWL", "W.......W", "W.......W", "G.......G", "W.......W", "W.......W", "LWWWuWWWL"},
            {"LWWWWWWWL", "W.......W", "W...n...W", "W.......W", "W...n...W", "W.......W", "LPPPPPPPL"},
    };

    private static final String[][] ARCHERY_RANGE = {
            {"CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC",
                    "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC"},
            {"FFF*F*FFF", "F.......F", "F.=...=.F", "F.......F", "F.......F", "F.......F", "F.......F", "F.......F", "F.......F",
                    "F.......F", "F.......F", "FqR.K.RQF", "FFFFgFFFF"},
            {"l..*.*..l", ".........", ".........", ".........", ".........", ".........", ".........", ".........", ".........",
                    ".........", ".........", ".........", "l.......l"},
    };

    /** The first watchtower: a log lookout with a bell, for any village. */
    private static final String[][] LOOKOUT = {
            {"O...O", ".....", ".....", ".....", "O...O"},
            {"O...O", ".....", "..O..", "..A..", "O...O"},
            {"O...O", ".....", "..O..", "..A..", "O...O"},
            {"O...O", ".....", "..O..", "..A..", "O...O"},
            {"O...O", ".....", "..O..", "..A..", "O...O"},
            {"O...O", ".....", "..O..", "..A..", "O...O"},
            {"PPPPP", "PPPPP", "PPOPP", "PPAPP", "PPPPP"},
            {"FFFFF", "F...F", "F.-.F", "F...F", "FFFFF"},
            {"l...l", ".....", ".....", ".....", "l...l"},
    };

    /** The last watchtower: a fortified tower with arrow slits, battlements, a bell and your banner. */
    private static final String[][] FORTIFIED_TOWER = {
            {"MMMMMMM", "MMMMMMM", "MMMMMMM", "MMMMMMM", "MMMMMMM", "MMMMMMM", "MMMMMMM"},
            {"LMMMMML", "M.A...M", "M.....M", "M.....M", "M.....M", "MK...RM", "LMMDMML"},
            {"LMMMMML", "M.A...M", "M.....M", "|.....|", "M.....M", "M.....M", "LMMuMML"},
            {"LWWWWWL", "W.A...W", "W.....W", "W.....W", "W.....W", "W.....W", "LWWWWWL"},
            {"LWW|W|L", "W.A...W", "|.....|", "W.....W", "|.....|", "W.....W", "LW|W|WL"},
            {"LWWWWWL", "W.A...W", "W.....W", "W.....W", "W.....W", "W.....W", "LWWWWWL"},
            {"LWW|W|L", "W.A...W", "|.....|", "W.....W", "|.....|", "W.....W", "LW|W|WL"},
            {"PPPPPPP", "PPAPPPP", "PPPPPPP", "PPPPPPP", "PPPPPPP", "PPPPPPP", "PPPPPPP"},
            {"M.M.M.M", ".......", "M.....M", "...-...", "M.....M", ".......", "M.M.M.M"},
            {"l.N...l", ".......", ".......", ".......", ".......", ".......", "l.....l"},
    };

    // ------------------------------------------------------------------ the Illager faction's camps

    /** A dark oak outpost tower with a crossbow platform and an Illager Standard at its foot. */
    private static final String[][] ILLAGER_TOWER = {
            {"δδδδδδδ", "δδδδδδδ", "δδδδδδδ", "δδδδδδδ", "δδδδδδδ", "δδδδδδδ", "δδδδδδδ"},
            {"βαααααβ", "α.A...α", "α.....α", "α.....α", "α.....α", "αR...Kα", "βαα.ααβ"},
            {"βαααααβ", "α.A...α", "α.....α", "γ.....γ", "α.....α", "α.....α", "βαα.ααβ"},
            {"βαααααβ", "α.A...α", "α.....α", "α.....α", "α.....α", "α.....α", "βαααααβ"},
            {"βαααααβ", "ααAαααα", "ααααααα", "ααααααα", "ααααααα", "ααααααα", "βαααααβ"},
            {"βααγααβ", "α.A...α", "γ.....γ", "α.....α", "γ.....γ", "α.....α", "βαγαγαβ"},
            {"βαααααβ", "α.A...α", "α.....α", "α.....α", "α.....α", "α.....α", "βαααααβ"},
            {"ααααααα", "ααAαααα", "ααααααα", "ααααααα", "ααααααα", "ααααααα", "ααααααα"},
            {"γγγγγγγ", "γ.....γ", "γ.....γ", "γ.....γ", "γ.....γ", "γ.....γ", "γγγγγγγ"},
            {"θ.....θ", ".......", ".......", ".......", ".......", ".......", "θ.....θ"},
    };

    /** A striped canvas tent on dark oak poles. */
    private static final String[][] ILLAGER_TENT = {
            {"#####", "#####", "#####", "#####", "#####"},
            {"β...β", ".....", ".R.K.", ".....", "β...β"},
            {"ζηζηζ", "ζ...ζ", "ζ...ζ", "ζ...ζ", "ζ...ζ"},
            {" ζηζ ", " ζηζ ", " ζηζ ", " ζηζ ", " ζηζ "},
            {"  η  ", "  η  ", "  η  ", "  η  ", "  η  "},
    };

    /** An iron cage: villagers kept captive inside are freed (and join you) when the camp falls. */
    private static final String[][] ILLAGER_CAGE = {
            {"δδδδδ", "δδδδδ", "δδδδδ", "δδδδδ", "δδδδδ"},
            {"β|||β", "|...|", "|...|", "|...|", "β|||β"},
            {"β|||β", "|...|", "|...|", "|...|", "β|||β"},
            {"θθθθθ", "θθθθθ", "θθθθθ", "θθθθθ", "θθθθθ"},
    };

    /** The camp's heart: the Illager Standard on a cobble plinth, ringed by wall posts. */
    private static final String[][] ILLAGER_STANDARD = {
            {"δδδδδ", "δεδεδ", "δδδδδ", "δεδεδ", "δδδδδ"},
            {"λ...λ", ".....", "..κ..", ".....", "λ...λ"},
            {"λ...λ", ".....", ".....", ".....", "λ...λ"},
    };

    /** The Overlord's hall: dark oak and black banners, a red carpet to a throne. */
    private static final String[][] ILLAGER_HALL = {
            {"δδδδδδδδδ", "δδδδδδδδδ", "δδδδδδδδδ", "δδδδδδδδδ", "δδδδδδδδδ", "δδδδδδδδδ", "δδδδδδδδδ", "δδδδδδδδδ", "δδδδδδδδδ", "δδδδδδδδδ", "δδδδδδδδδ"},
            {"βαααααααβ", "α.μ.ξ.μ.α", "α...ν...α", "α,..ν..Rα", "α...ν...α", "αK..ν..Kα", "α...ν...α", "α...ν...α", "α...ν...α", "α...ν...α", "βααα.αααβ"},
            {"βαααααααβ", "α.μ...μ.α", "α.......α", "γ.......γ", "α.......α", "α.......α", "γ.......γ", "α.......α", "α.......α", "α.......α", "βααα.αααβ"},
            {"βαααααααβ", "α.......α", "α.......α", "α.......α", "α.......α", "α.......α", "α.......α", "α.......α", "α.......α", "α.......α", "βαααααααβ"},
            {"θθθθθθθθθ", "θθθθθθθθθ", "θθθθθθθθθ", "θθθθθθθθθ", "θθθθθθθθθ", "θθθθθθθθθ", "θθθθθθθθθ", "θθθθθθθθθ", "θθθθθθθθθ", "θθθθθθθθθ", "θθθθθθθθθ"},
            {"γ.γ.γ.γ.γ", ".........", "γ.......γ", ".........", "γ.......γ", ".........", "γ.......γ", ".........", "γ.......γ", ".........", "γ.γ.γ.γ.γ"},
    };

    /** A padded layer, empty except one character on the ridge (a bell, a flag...). */
    private static String[] ridge(String[][] walls, char c) {
        int W = walls[0][0].length() + 2, D = walls[0].length + 2;
        String[] rows = new String[D];
        for (int z = 0; z < D; z++) {
            char[] row = " ".repeat(W).toCharArray();
            if (z == D / 2) row[W / 2] = c;
            rows[z] = new String(row);
        }
        return rows;
    }

    static {
        add("cottage", "Cottage", "A family home: two beds, a chest, a crafting table and a pitched roof.", 1, null, 0, true, house(COTTAGE));
        add("longhouse", "Longhouse", "A long family hall with three beds side by side and room for storage.", 1, null, 0, true, house(LONGHOUSE));
        add("farmhouse", "Farmhouse", "A wide house with a covered porch across the front.", 1, null, 0, true, house(FARMHOUSE));
        add("townhouse", "Townhouse", "A narrow house with a steep roof, for tight streets.", 1, null, 0, true, house(TOWNHOUSE));
        hidden("cottage_2", "Two-Storey Cottage", "A cottage with an upstairs.", 1, "storeys", 0, true, storeyHouse(COTTAGE));
        hidden("longhouse_2", "Two-Storey Longhouse", "A longhouse with an upstairs.", 1, "storeys", 0, true, storeyHouse(LONGHOUSE));
        hidden("farmhouse_2", "Two-Storey Farmhouse", "A farmhouse with an upstairs.", 1, "storeys", 0, true, storeyHouse(FARMHOUSE));
        hidden("townhouse_2", "Two-Storey Townhouse", "A townhouse with an upstairs.", 1, "storeys", 0, true, storeyHouse(TOWNHOUSE));
        add("castle", "Castle", "A walled castle with four towers, a gatehouse and a keep. Sleeps six soldiers in the keep and flies your flag.",
                1, "stone", 2, true, castle());
        hidden("orc_hut", "Orc Hut", "", 1, null, 0, false, ORC_HUT);
        hidden("orc_totem", "War Totem", "", 1, null, 0, false, ORC_TOTEM);
        hidden("orc_warlord_hut", "Warlord's Hut", "", 1, null, 0, false, ORC_WARLORD_HUT);
        add("stables", "Stables", "Three stalls, hay racks and a trough. Breeds horses for your kingdom and saddles them for soldiers who have none:"
                + " knights on Clydesdales, horse archers on Mustangs. Comes with two horses.", 1, "horsemanship", 0, true, house(STABLES));
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

        add("lookout", "Lookout Tower", "A log lookout with a bell. One archer keeps watch from the top, it rings the bell when enemies come within"
                + " 40 blocks, and you can claim land up to a chunk around it even where it doesn't touch your borders. Sneak + right-click its"
                + " sign to raise it into a Stone Watchtower, then a Fortified Tower.", 1, null, 0, true, LOOKOUT);
        add("watchtower", "Stone Watchtower", "A tall stone tower with arrow slits and a lookout platform. Garrisons two archers, sees 56 blocks,"
                + " and lets you claim land up to 2 chunks around it. Flies your flag.", 1, "stone", 2, true, new String[][]{
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

        add("fortified_tower", "Fortified Tower", "A great tower with battlements, arrow slits, a bell and your banner. Garrisons four archers,"
                + " sees 72 blocks, and lets you claim land up to 3 chunks around it.", 1, "fortification", 2, true, FORTIFIED_TOWER);
        add("tavern", "Tavern", "Ale, a hearth and tables for travellers. An Innkeeper brings in +3 Influence a day, and travellers who"
                + " stop by may stay and join your kingdom (if you have a free bed and food to spare).", 1, null, 0, true, house(TAVERN, ridgeFlag(TAVERN)));
        add("chapel", "Chapel", "Pews, an altar and a bell. A Priest heals and blesses your people within 32 blocks and adds +2 Influence a day.",
                1, "faith", 0, true, house(CHAPEL, ridge(CHAPEL, '-')));
        add("mill", "Mill", "Grindstones and hay. A Miller grinds wheat from the storehouse into Flour.", 1, "milling", 0, true, house(MILL));
        add("bakery", "Bakery", "Ovens and a smoker. A Baker turns Flour into bread (two loaves a sack), or bakes plain wheat when there's no flour.",
                1, "milling", 0, true, house(BAKERY));
        add("butcher", "Butcher", "A smoker and a block. The Butcher keeps the herds near it: breeds cows, pigs, sheep and chickens with"
                + " grain from the storehouse and culls the extras into cooked meat, leather, wool and feathers.", 1, "husbandry", 0, true, house(BUTCHER));
        add("mine", "Mine", "A pithead with a blast furnace. A Miner brings up stone, coal and ore every few minutes: iron, copper, lapis and"
                + " redstone, sometimes gold, and in stone or better, now and then a diamond.", 1, "mining", 0, true, house(MINE));
        add("lumber_mill", "Lumber Mill", "Saws and log piles. A Sawyer cuts logs from the storehouse into planks (6 a log instead of 4) and sticks.",
                1, "timber", 1, true, house(LUMBER_MILL));
        add("harbor", "Harbor", "A boathouse with a slipway. A Fisher brings in fish (more if the harbor stands by open water), and now and then"
                + " something from the deep. Cooked in Stone or better.", 1, "seafaring", 0, true, house(HARBOR));
        add("archery_range", "Archery Range", "Targets, hay bales and a fletcher's table. Every archer in your kingdom hits 25% harder"
                + " (40% in Stone or better).", 1, "archery", 0, true, ARCHERY_RANGE);
        hidden("illager_tower", "Illager Tower", "", 1, null, 0, false, ILLAGER_TOWER);
        hidden("illager_tent", "Illager Tent", "", 1, null, 0, false, ILLAGER_TENT);
        hidden("illager_cage", "Illager Cage", "", 1, null, 0, false, ILLAGER_CAGE);
        hidden("illager_standard", "Illager Standard", "", 1, null, 0, false, ILLAGER_STANDARD);
        hidden("illager_hall", "Overlord's Hall", "", 1, null, 0, false, ILLAGER_HALL);
        add("market_stall", "Market Stall", "A Market under an awning striped in your colors.", 0, null, 0, false, new String[][]{
                {"F...F", ".....", "..T..", ".....", "F...F"},
                {"F...F", ".....", ".....", ".....", "F...F"},
                {"XZXZX", "XZXZX", "XZXZX", "XZXZX", "XZXZX"}});

        add("wall_gate", "Wall Gate", "A stone gateway with fence gates, to set into your walls.", 0, "stone", 2, false, new String[][]{
                {"LgggL"}, {"M...M"}, {"M...M"}, {"MMMMM"}, {"M M M"}});

        wall("stone_wall", "Stone Wall", "A wall 4 high with battlements, in your tier's stone. Click two points to draw it.", "stone", 2, "MMMMM", "MMMM ");
        wall("palisade", "Palisade", "A sharpened spruce log fence, 4 high. Click two points to draw it.", null, 0, "OOOOo", "OOOOo");
        ALL.put("road", new Structure("road", "Road", "A road three blocks wide that follows the lie of the land: packed earth, then cobbles,"
                + " then stone setts as your tier rises. Everyone (and every caravan) travels faster on a road, and bandits keep off it."
                + " Click two points to draw it.", Kind.WALL, 0, List.<String[]>of(new String[]{"$^"}), null, 0, false));
    }

    public static @Nullable Structure get(String id) {
        Structure s = ALL.get(id);
        return s != null ? s : HIDDEN.get(id);
    }

    public static java.util.Collection<Structure> all() {
        return ALL.values();
    }
}
