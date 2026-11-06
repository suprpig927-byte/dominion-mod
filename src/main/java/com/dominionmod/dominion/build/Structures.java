package com.dominionmod.dominion.build;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The premade buildings and walls builders can raise.
 *
 * <p>Layouts are layers from the bottom up; each layer is rows from north (back) to south (front,
 * where the door is), each row read west to east. The first {@code sink} layers replace the ground.
 * Characters:
 * <pre>
 *  ' ' leave alone      '.' clear to air       C cobblestone   M stone bricks   P oak planks
 *  L oak log            s oak slab             G glass pane     D door (lower)   u door (upper, placed with D)
 *  h bed head           b bed foot (in the kingdom's primary color; place h directly north of b)
 *  K chest              R barrel               Q crafting table l lantern        A ladder (on the wall to its north)
 *  F oak fence          g oak fence gate       f farmland       W water          w wheat (planted from seeds)
 *  N kingdom flag       T market block         X primary wool   Z secondary wool O spruce log  o spruce fence
 *  &gt; spruce stairs rising east     &lt; spruce stairs rising west
 * </pre>
 */
public final class Structures {
    private Structures() {}

    public enum Kind { BUILDING, WALL }

    public record Structure(String id, String name, String description, Kind kind, int sink, List<String[]> layers) {
        public int width() { return kind == Kind.WALL ? 1 : layers.get(0)[0].length(); }
        public int depth() { return kind == Kind.WALL ? 1 : layers.get(0).length; }
        public int height() { return layers.size(); }
    }

    private static final Map<String, Structure> ALL = new LinkedHashMap<>();

    private static void add(String id, String name, String desc, int sink, String[]... layers) {
        ALL.put(id, new Structure(id, name, desc, Kind.BUILDING, sink, List.of(layers)));
    }

    /** A wall is one column, repeated along the line you draw. Odd and even columns can differ (for battlements). */
    private static void wall(String id, String name, String desc, String even, String odd) {
        String[][] layers = new String[even.length()][];
        for (int y = 0; y < even.length(); y++) layers[y] = new String[]{"" + even.charAt(y) + odd.charAt(y)};
        ALL.put(id, new Structure(id, name, desc, Kind.WALL, 0, List.of(layers)));
    }

    static {
        addLayers("cottage", "Cottage", "A timber-and-stone home with two beds, a chest, a crafting table and a pitched roof.", 1, withRoof(
                new String[][]{
                        {"CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC"},
                        {"LCCCCCL", "Ch...hC", "Cb...bC", "C..l..C", "C.....C", "CQ...KC", "LCCDCCL"},
                        {"LPPGPPL", "P.....P", "P.....P", "G.....G", "P.....P", "P.....P", "LPPuPPL"},
                        {"LPPPPPL", "P.....P", "P.....P", "P.....P", "P.....P", "P.....P", "LPPPPPL"}}));

        addLayers("barracks", "Barracks", "Bunks for four soldiers, with chests and barrels for their gear. Flies your flag from the roof.", 1, withRoof(
                new String[][]{
                        {"CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC"},
                        {"LMMMMMMML", "Mh.h.h.hM", "Mb.b.b.bM", "M...l...M", "M.......M", "MKR...RKM", "LMMMDMMML"},
                        {"LPPGPGPPL", "P.......P", "P.......P", "G.......G", "P.......P", "P.......P", "LPGPuPGPL"},
                        {"LPPPPPPPL", "P.......P", "P.......P", "P.......P", "P.......P", "P.......P", "LPPPPPPPL"}},
                new String[]{"         ", "         ", "         ", "    N    ", "         ", "         ", "         "}));
        add("farm", "Wheat Farm", "A fenced 7x7 wheat field around a water source. Give it a Farmer.", 1,
                new String[]{"CCCCCCCCC", "CfffffffC", "CfffffffC", "CfffffffC", "CfffWfffC", "CfffffffC", "CfffffffC", "CfffffffC", "CCCCCCCCC"},
                new String[]{"FFFFFFFFF", "FwwwwwwwF", "FwwwwwwwF", "FwwwwwwwF", "Fwww.wwwF", "FwwwwwwwF", "FwwwwwwwF", "FwwwwwwwF", "FFFFgFFFF"});

        add("watchtower", "Watchtower", "A stone tower with arrow slits and a lookout platform. Flies your flag.", 1,
                new String[]{"MMMMM", "MMMMM", "MMMMM", "MMMMM", "MMMMM"},
                new String[]{"MMMMM", "M.A.M", "M...M", "M...M", "MMDMM"},
                new String[]{"MMMMM", "M.A.M", "M...M", "M...M", "MMuMM"},
                new String[]{"MMMMM", "M.A.M", "M...M", "M...M", "MMMMM"},
                new String[]{"MM.MM", "M.A.M", ".....", "M...M", "MM.MM"},
                new String[]{"MMMMM", "M.A.M", "M...M", "M...M", "MMMMM"},
                new String[]{"MMMMM", "M.A.M", "M...M", "M...M", "MMMMM"},
                new String[]{"PPPPP", "PPAPP", "PPPPP", "PPPPP", "PPPPP"},
                new String[]{"FFFFF", "F...F", "F.N.F", "F...F", "FFFFF"});

        addLayers("storehouse", "Storehouse", "A stone-footed warehouse lined with chests and barrels under a tall roof. Its chests join your storehouse, wherever you build it.", 1, withRoof(
                new String[][]{
                        {"CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC"},
                        {"LMMMMMMML", "MRKKRKKRM", "M.......M", "MR.....RM", "M...l...M", "MR.....RM", "LMMMDMMML"},
                        {"LPPPPPPPL", "PR.....RP", "P.......P", "G.......G", "P.......P", "PR.....RP", "LPPPuPPPL"},
                        {"LPPPPPPPL", "P.......P", "P.......P", "G.......G", "P.......P", "P.......P", "LPGPPPGPL"}}));

        add("market_stall", "Market Stall", "A Market under an awning striped in your colors.", 0,
                new String[]{"F...F", ".....", "..T..", ".....", "F...F"},
                new String[]{"F...F", ".....", ".....", ".....", "F...F"},
                new String[]{"XZXZX", "XZXZX", "XZXZX", "XZXZX", "XZXZX"});

        add("wall_gate", "Wall Gate", "A stone gateway with fence gates, to set into your walls.", 0,
                new String[]{"MgggM"},
                new String[]{"M...M"},
                new String[]{"M...M"},
                new String[]{"MMMMM"},
                new String[]{"M M M"});

        wall("stone_wall", "Stone Wall", "Stone brick wall, 4 high with battlements. Click two points to draw it.", "MMMMM", "MMMM ");
        wall("palisade", "Palisade", "Sharpened spruce log fence, 4 high. Click two points to draw it.", "OOOOo", "OOOOo");
    }

    /** Walls (bottom up), then a gable roof of spruce stairs with its ridge running front to back, then any extra layers. */
    private static String[][] withRoof(String[][] walls, String[]... extra) {
        int w = walls[0][0].length(), d = walls[0].length;
        java.util.List<String[]> out = new java.util.ArrayList<>(java.util.List.of(walls));
        for (int k = 0; k <= w / 2; k++) {
            String[] rows = new String[d];
            for (int z = 0; z < d; z++) {
                StringBuilder row = new StringBuilder();
                for (int x = 0; x < w; x++) {
                    char c = ' ';
                    if (x == k && x == w - 1 - k) c = 'P';
                    else if (x == k) c = '>';
                    else if (x == w - 1 - k) c = '<';
                    else if (x > k && x < w - 1 - k) c = (z == 0 || z == d - 1) ? 'P' : '.';
                    row.append(c);
                }
                rows[z] = row.toString();
            }
            out.add(rows);
        }
        for (String[] e : extra) out.add(e);
        return out.toArray(new String[0][]);
    }

    private static void addLayers(String id, String name, String desc, int sink, String[][] layers) {
        ALL.put(id, new Structure(id, name, desc, Kind.BUILDING, sink, List.of(layers)));
    }

    public static @Nullable Structure get(String id) {
        return ALL.get(id);
    }

    public static java.util.Collection<Structure> all() {
        return ALL.values();
    }
}
