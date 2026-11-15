package faygolover.rpperks.gametest;

import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Проверка «каждое поле состояния попадает в сохранение и читается обратно» без ручного списка полей.
 * <p>
 * Для каждого простого поля объекта (число, флаг, строка, перечисление; вложенные объекты и массивы своих классов
 * обходятся вглубь) берётся свежий объект, меняется одно это поле, объект пишется в NBT:
 * <ul>
 *   <li>тег не отличается от тега свежего объекта — поле <b>не сохраняется</b>;</li>
 *   <li>тег читается в другой свежий объект и пишется снова, результат другой — поле пишется, но
 *   <b>читается не так</b>.</li>
 * </ul>
 * Коллекции и карты так не проверить (нечем наполнить вслепую) — они возвращаются отдельным списком.
 * Новое поле, забытое в записи или чтении, роняет тест само: список известных исключений держит вызывающий.
 */
final class NbtCoverage {
    private NbtCoverage() {}

    static final class Result {
        /** Поля, изменение которых не меняет тег. */
        final TreeSet<String> notSaved = new TreeSet<>();
        /** Поля, которые пишутся, но после чтения и повторной записи дают другой тег. */
        final TreeSet<String> notRestored = new TreeSet<>();
        /** Коллекции и карты: этим способом не проверяются. */
        final TreeSet<String> skipped = new TreeSet<>();
        /** Поля, на которых запись или чтение упали (с текстом ошибки). */
        final TreeSet<String> errors = new TreeSet<>();
        int checked;
    }

    private record Step(Field field, int index) {}

    static <T> Result check(Supplier<T> fresh, Function<T, CompoundTag> write, BiConsumer<T, CompoundTag> read, String ownPackage) {
        Result r = new Result();
        CompoundTag base = write.apply(fresh.get());
        List<List<Step>> slots = new ArrayList<>();
        collect(fresh.get(), new ArrayList<>(), slots, r, ownPackage, 0);
        for (List<Step> path : slots) {
            String name = name(path);
            try {
                T obj = fresh.get();
                mutate(obj, path);
                CompoundTag t1 = write.apply(obj);
                r.checked++;
                if (t1.equals(base)) {
                    r.notSaved.add(name);
                    continue;
                }
                T back = fresh.get();
                read.accept(back, t1);
                if (!t1.equals(write.apply(back))) r.notRestored.add(name);
            } catch (Throwable e) {
                r.errors.add(name + ": " + e);
            }
        }
        return r;
    }

    private static void collect(Object obj, List<Step> prefix, List<List<Step>> out, Result r, String ownPackage, int depth) {
        if (obj == null || depth > 5) return;
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || f.isSynthetic()) continue;
                f.setAccessible(true);
                Class<?> t = f.getType();
                List<Step> here = new ArrayList<>(prefix);
                Object value;
                try {
                    value = f.get(obj);
                } catch (IllegalAccessException e) {
                    continue;
                }
                if (simple(t)) {
                    if (Modifier.isFinal(mod)) continue;
                    here.add(new Step(f, -1));
                    out.add(here);
                } else if (t.isArray()) {
                    if (value == null || Array.getLength(value) == 0) continue;
                    Class<?> ct = t.getComponentType();
                    int last = Array.getLength(value) - 1;
                    for (int i : last == 0 ? new int[] {0} : new int[] {0, last}) {
                        List<Step> el = new ArrayList<>(prefix);
                        el.add(new Step(f, i));
                        if (simple(ct)) out.add(el);
                        else if (ct.getName().startsWith(ownPackage)) collect(Array.get(value, i), el, out, r, ownPackage, depth + 1);
                    }
                } else if (Collection.class.isAssignableFrom(t) || Map.class.isAssignableFrom(t)) {
                    here.add(new Step(f, -1));
                    r.skipped.add(name(here));
                } else if (t.getName().startsWith(ownPackage)) {
                    here.add(new Step(f, -1));
                    collect(value, here, out, r, ownPackage, depth + 1);
                }
            }
        }
    }

    private static boolean simple(Class<?> t) {
        return t.isPrimitive() || t == String.class || t.isEnum() || Number.class.isAssignableFrom(t) || t == Boolean.class;
    }

    private static String name(List<Step> path) {
        StringBuilder sb = new StringBuilder();
        for (Step s : path) {
            if (sb.length() > 0) sb.append('.');
            sb.append(s.field.getName());
            if (s.index >= 0) sb.append('[').append(s.index == 0 ? "0" : "last").append(']');
        }
        return sb.toString();
    }

    private static void mutate(Object root, List<Step> path) throws IllegalAccessException {
        Object cur = root;
        for (int i = 0; i < path.size() - 1; i++) {
            Step s = path.get(i);
            Object v = s.field.get(cur);
            cur = s.index >= 0 ? Array.get(v, s.index) : v;
        }
        Step last = path.get(path.size() - 1);
        if (last.index >= 0) {
            Object arr = last.field.get(cur);
            Array.set(arr, last.index, changed(Array.get(arr, last.index), arr.getClass().getComponentType()));
        } else {
            last.field.set(cur, changed(last.field.get(cur), last.field.getType()));
        }
    }

    private static Object changed(Object v, Class<?> t) {
        if (t == double.class || t == Double.class) {
            double d = v == null ? 0 : (Double) v;
            return Double.isFinite(d) && Math.abs(d) < 1e8 ? d + 1.37 : 1.37;
        }
        if (t == float.class || t == Float.class) {
            float d = v == null ? 0 : (Float) v;
            return Float.isFinite(d) && Math.abs(d) < 1e8f ? d + 1.5f : 1.5f;
        }
        if (t == int.class || t == Integer.class) {
            int d = v == null ? 0 : (Integer) v;
            return d > 1_000_000 || d < -1_000_000 ? 1 : d + 1;
        }
        if (t == long.class || t == Long.class) {
            long d = v == null ? 0 : (Long) v;
            return d > 1_000_000_000L || d < -1_000_000_000L ? 1L : d + 1;
        }
        if (t == short.class || t == Short.class) return (short) ((v == null ? 0 : (Short) v) + 1);
        if (t == byte.class || t == Byte.class) return (byte) ((v == null ? 0 : (Byte) v) + 1);
        if (t == boolean.class || t == Boolean.class) return v == null || !(Boolean) v;
        if (t == char.class) return (char) ((Character) v + 1);
        if (t == String.class) return v == null ? "x" : v + "x";
        if (t.isEnum()) {
            Object[] all = t.getEnumConstants();
            int i = v == null ? -1 : ((Enum<?>) v).ordinal();
            return all[(i + 1) % all.length];
        }
        return v;
    }
}
