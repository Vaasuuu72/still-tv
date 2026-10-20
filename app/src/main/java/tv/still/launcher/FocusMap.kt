package tv.still.launcher

/** Deterministic D-pad focus between stacked sections (header pills, favourites dock, app grid).
 * Pure so every move is unit-tested; the Activity only maps positions to views. */
internal class FocusMap(private val sections: List<Section>) {
    data class Section(val count: Int, val columns: Int)
    data class Pos(val section: Int, val index: Int)
    enum class Direction { UP, DOWN, LEFT, RIGHT }

    fun isValid(pos: Pos) = pos.section in sections.indices && pos.index in 0 until sections[pos.section].count

    /** [remembered] is the last index focused in the section being entered, if the caller knows it. */
    fun move(from: Pos, direction: Direction, remembered: Int = -1): Pos {
        val pos = clamp(from) ?: return from
        val s = sections[pos.section]
        val col = pos.index % s.columns
        return when (direction) {
            Direction.LEFT -> if (col > 0) Pos(pos.section, pos.index - 1) else pos
            Direction.RIGHT -> if (col < s.columns - 1 && pos.index + 1 < s.count) Pos(pos.section, pos.index + 1) else pos
            Direction.UP -> when {
                pos.index >= s.columns -> Pos(pos.section, pos.index - s.columns)
                else -> neighbour(pos.section, -1)?.let { enterFromBelow(it, col, s.columns, remembered) } ?: pos
            }
            Direction.DOWN -> when {
                pos.index + s.columns < s.count -> Pos(pos.section, pos.index + s.columns)
                pos.index / s.columns < (s.count - 1) / s.columns -> Pos(pos.section, s.count - 1)
                else -> neighbour(pos.section, +1)?.let { enterFromAbove(it, pos.section, col, s.columns, remembered) } ?: pos
            }
        }
    }

    private fun clamp(pos: Pos): Pos? {
        if (pos.section in sections.indices && sections[pos.section].count > 0)
            return Pos(pos.section, pos.index.coerceIn(0, sections[pos.section].count - 1))
        return sections.indexOfFirst { it.count > 0 }.takeIf { it >= 0 }?.let { Pos(it, 0) }
    }

    private fun neighbour(section: Int, step: Int): Int? {
        var i = section + step
        while (i in sections.indices) { if (sections[i].count > 0) return i; i += step }
        return null
    }

    /** Header (section 0) is entered at the remembered pill, else the last one; grids by screen position. */
    private fun enterFromBelow(target: Int, col: Int, columns: Int, remembered: Int): Pos {
        val t = sections[target]
        if (target == 0) return Pos(0, if (remembered in 0 until t.count) remembered else t.count - 1)
        val lastRowStart = ((t.count - 1) / t.columns) * t.columns
        val lastRowLength = t.count - lastRowStart
        return Pos(target, lastRowStart + mapColumn(col, columns, t.columns).coerceAtMost(lastRowLength - 1))
    }

    private fun enterFromAbove(target: Int, from: Int, col: Int, columns: Int, remembered: Int): Pos {
        val t = sections[target]
        val firstRowLength = minOf(t.columns, t.count)
        if (from == 0) return Pos(target, if (remembered in 0 until firstRowLength) remembered else 0)
        return Pos(target, mapColumn(col, columns, t.columns).coerceAtMost(firstRowLength - 1))
    }

    /** Same relative horizontal position on screen: centre of column [col] of [from] columns. */
    private fun mapColumn(col: Int, from: Int, to: Int) = ((col + 0.5f) / from * to).toInt().coerceIn(0, to - 1)
}
