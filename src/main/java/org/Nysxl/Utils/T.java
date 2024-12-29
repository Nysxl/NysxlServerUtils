package org.Nysxl.Utils;

import java.util.Objects;

/**
 * A generic utility class for storing pairs of objects.
 *
 * @param <F> The type of the first element.
 * @param <S> The type of the second element.
 */
public class T<F, S> {
    private final F first;
    private final S second;

    public T(F first, S second) {
        this.first = first;
        this.second = second;
    }

    public F getFirst() {
        return first;
    }

    public S getSecond() {
        return second;
    }

    /**
     * Creates a new pair instance.
     *
     * @param first  The first element.
     * @param second The second element.
     * @param <F>    The type of the first element.
     * @param <S>    The type of the second element.
     * @return A new pair containing the provided elements.
     */
    public static <F, S> T<F, S> of(F first, S second) {
        return new T<>(first, second);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        T<?, ?> t = (T<?, ?>) o;
        return Objects.equals(first, t.first) && Objects.equals(second, t.second);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }

    @Override
    public String toString() {
        return "T{" +
                "first=" + first +
                ", second=" + second +
                '}';
    }
}
