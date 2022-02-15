package ru.teconD.mfkFilter.model;

import java.util.StringJoiner;

/**
 * Класс хранения индекса и смещения.
 * Сортировка по полю индекса
 * @author Maksim Shchelkonogov
 */
public class RemoveIndex implements Comparable<RemoveIndex> {

    private int startIndex;
    private int offset;

    public RemoveIndex(int startIndex, int offset) {
        this.startIndex = startIndex;
        this.offset = offset;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public int getOffset() {
        return offset;
    }

    @Override
    public int compareTo(RemoveIndex o) {
        return Integer.compare(this.startIndex, o.startIndex);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", RemoveIndex.class.getSimpleName() + "[", "]")
                .add("startIndex=" + startIndex)
                .add("offset=" + offset)
                .toString();
    }
}
