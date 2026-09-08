package com.seggellion.britannia_mod.quest.action;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The {@code subject} object of an action event (protocol section 2.1): an ORDERED map of the
 * facts the server observed about the mutation that just succeeded.
 *
 * <p>Ordering is part of the contract only in the sense that the encoder is byte-compared against
 * a frozen fixture; keeping insertion order here is what lets a caller write the fields in the
 * order the fixture shows without the encoder having to know about any particular action.
 *
 * <p>Three value kinds exist, matching the three JSON scalars section 2.1 uses: text, flag and
 * whole number. Text is validated at construction -- no quote, backslash or control character can
 * enter -- so the encoder never has to escape and its output is fully determined by its input.
 */
public final class QuestActionSubject {
    public static final int MAX_FIELDS = 16;
    public static final int MAX_TEXT_LENGTH = 255;

    /** One subject value. Sealed so the encoder's switch is total. */
    public sealed interface Value permits Text, Flag, Number {
        /** The comparison form a journal {@code match}/{@code require_bound} entry is checked against. */
        String comparable();
    }

    public record Text(String value) implements Value {
        public Text {
            Objects.requireNonNull(value, "value");
            if (value.isEmpty() || value.length() > MAX_TEXT_LENGTH) {
                throw new IllegalArgumentException("subject text is outside its size bound");
            }
            if (value.chars().anyMatch(c -> c == '"' || c == '\\' || Character.isISOControl(c))) {
                throw new IllegalArgumentException("subject text carries a character the encoder cannot emit");
            }
        }

        @Override
        public String comparable() {
            return value;
        }
    }

    public record Flag(boolean value) implements Value {
        @Override
        public String comparable() {
            return Boolean.toString(value);
        }
    }

    public record Number(long value) implements Value {
        @Override
        public String comparable() {
            return Long.toString(value);
        }
    }

    private final Map<String, Value> fields;

    private QuestActionSubject(Map<String, Value> fields) {
        this.fields = Collections.unmodifiableMap(fields);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Value> fields() {
        return fields;
    }

    public Set<String> names() {
        return fields.keySet();
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    /** The comparison form of one field, or null when the subject does not carry it. */
    public String comparable(String name) {
        Value value = fields.get(name);
        return value == null ? null : value.comparable();
    }

    /** Whether every field {@code action} requires is present. */
    public boolean satisfies(QuestAction action) {
        return action != null && fields.keySet().containsAll(action.requiredSubjectFields());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof QuestActionSubject subject && fields.equals(subject.fields);
    }

    @Override
    public int hashCode() {
        return fields.hashCode();
    }

    @Override
    public String toString() {
        return "QuestActionSubject" + fields;
    }

    public static final class Builder {
        private final LinkedHashMap<String, Value> fields = new LinkedHashMap<>();

        private Builder() {}

        public Builder text(String name, String value) {
            return put(name, new Text(value));
        }

        /** Adds the field only when {@code value} is non-blank; optional fields use this. */
        public Builder optionalText(String name, String value) {
            return value == null || value.isBlank() ? this : text(name, value);
        }

        public Builder flag(String name, boolean value) {
            return put(name, new Flag(value));
        }

        public Builder number(String name, long value) {
            return put(name, new Number(value));
        }

        public Builder put(String name, Value value) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            if (!name.matches("[a-z0-9_]{1,64}")) {
                throw new IllegalArgumentException("subject field name is not a contract key: " + name);
            }
            if (!fields.containsKey(name) && fields.size() >= MAX_FIELDS) {
                throw new IllegalArgumentException("a subject carries at most " + MAX_FIELDS + " fields");
            }
            fields.put(name, value);
            return this;
        }

        public QuestActionSubject build() {
            return new QuestActionSubject(new LinkedHashMap<>(fields));
        }
    }
}
