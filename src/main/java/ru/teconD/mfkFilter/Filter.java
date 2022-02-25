package ru.teconD.mfkFilter;

/**
 * Интерфейс для фильтрации
 * @author Maksim Shchelkonogov
 */
public interface Filter<T> {

    /**
     * Фильтруем переданные данный
     * @param t входные данные, для фильтрафции
     * @return отфильтрованные данные
     */
    T filter(T t) throws FilterException;
}
