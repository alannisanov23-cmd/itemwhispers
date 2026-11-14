# Item Whispers (Fabric, Minecraft 26.3)

Your tools talk to you. A short in-character line appears in the text above your hotbar:

- when you **pick up** a tool
- every **30-60 seconds** while you keep holding it
- when it gains a **new enchantment**
- when its durability is **10% or lower** (and sometimes under 25%)

Covers pickaxes, axes, shovels, hoes and swords. Lines change with the tool type and its enchantments
(Silk Touch, Fortune, Efficiency, Unbreaking, Mending, Sharpness).

Client-side only, no mixins and no rendering changes, so it works with Enchant Tag, Silk & Fortune Glow and the
Enchantment Outlines resource pack. To turn it off, remove the jar from `mods`.

## Build
Upload this folder's contents to a GitHub repo, open Actions, download the artifact, and use the jar WITHOUT -sources.

## Tweak
Edit the line lists at the bottom of `ItemWhispersClient.java` to write your own. `MIN_GAP_TICKS` and `resetIdle()`
control how often it talks.
