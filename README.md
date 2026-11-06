# Dominion

A NeoForge 26.3 Minecraft mod: found a kingdom, recruit villagers, put your people to work, raise an army and claim the land chunk by chunk.

## Getting started
1. Craft a **Town Hall** (planks around a lectern, a gold ingot on top, stone bricks along the bottom) and place it. Name your kingdom, pick a primary and secondary color, then design your flag: a base color plus up to 8 pattern layers, each in its own color (or copy a banner you're holding). The flag flies over the Town Hall and is worn on every soldier's tabard. The ruler can redesign it any time from the Realm screen. You get 20 Gold Coins, a Command Baton and 30 Influence.
2. Put **chests or barrels within 6 blocks of the Town Hall**. They are your storehouse: workers fill them, citizens eat from them, taxes go in and wages come out.
3. Place **beds** on your land (one per citizen), then right-click villagers standing on your land while holding **5 Gold Coins** to recruit them.
4. Hand a citizen a tool to give them a job, right where they stand:
   - Hoe: **Farmer** (harvests and replants crops nearby, sows empty farmland)
   - Axe: **Lumberjack** (fells whole trees nearby and replants them)
   - Sword or spear: **Militia**
   - Bow: **Archer**
   - Builder's Hammer (3 iron ingots over 2 sticks): **Builder**
   Hand them armor and they wear it. Sneak + right-click with an empty hand takes their gear back. Right-click with an empty hand shows their status.
5. Every Minecraft day each citizen eats one food item from the storehouse. Workers pay 1 coin in tax, soldiers draw 1 coin in wages. Three days hungry or unpaid and they leave.
6. Sell surplus at a **Market** block for Gold Coins (right-click it with a stack; right-click empty-handed for prices).

## Building
- Open the **Build** tab of the Realm screen, pick a structure and take its blueprint: Cottage (2 beds), Barracks (4 beds, flies your flag), Wheat Farm, Watchtower, Storehouse, Market Stall, Wall Gate, Stone Wall, Palisade.
- Right-click the ground with the blueprint to mark it out (an outline shows while you hold it; the white edge is the front). Sneak + right-click to rotate, right-click the same spot again to confirm.
- Walls: click where the wall starts, click where it ends (straight or diagonal, up to 64 blocks, following the ground), then click the end again to confirm.
- Builders fetch materials from the storehouse, clear the site and place every block. Beds, wool and banners come out in your kingdom's colors; any color of bed or wool works as material. They tell you when they're missing something.

## Beds
- Each citizen gets their own bed automatically (the nearest free one on your land) and sleeps in it at night.
- To move someone: select just them with the Command Baton, then right-click a bed with it. Whoever slept there swaps into their old bed. Right-click a bed with nobody selected to see whose it is.
- The **Citizens** tab of the Realm screen lists everyone with their job and bed.

## Land
- Press **K** (or right-click your Town Hall) for the Realm screen: your stats, a map of nearby chunks, and Claim / Release buttons.
- Claiming a chunk costs Influence (8 + 4 per chunk you own) and it must border your land. Influence comes in through the day: 5 + 3 per citizen.
- Outsiders can't break, place or use blocks on your land. Monster spawning can be switched off for your land.
- Hold the Command Baton to see borders in each kingdom's color.

## Command Baton
- Right-click a citizen: select or deselect. Sneak + right-click a citizen: select every soldier within 24 blocks.
- Right-click the air: give the current order. Sneak + right-click: switch order (**Rally**, **Hold**, **Return to duty**, **Clear selection**).
- **Rally**: soldiers fall in behind you, two abreast while you move and in a battle line when you stop.
- Right-click a block: soldiers march there and hold it; selected workers make it their new work spot.
- Right-click an enemy: selected soldiers **charge** it.
- Soldiers fight harder (Strength) near any member of their kingdom. If you die, nearby troops waver and fall back.

## Commands
`/dominion claim | unclaim | realm | list | invite <player> | join | leave | crown <player>`
Operators: `/dominion admin influence <amount>`, `/dominion admin disband <kingdom>`

## Building
Pushes build the jar on GitHub Actions (Build workflow, artifact `dominion-mod`). Textures are painted by `tools/gen_textures.py`.
