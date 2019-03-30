package me.zeroeightsix.fiber.annotations;

import com.google.common.primitives.Primitives;
import me.zeroeightsix.fiber.ConfigOperations;
import me.zeroeightsix.fiber.builder.ConfigValueBuilder;
import me.zeroeightsix.fiber.ir.ConfigNode;
import me.zeroeightsix.fiber.annotations.conventions.NoNamingConvention;
import me.zeroeightsix.fiber.annotations.conventions.SettingNamingConvention;
import me.zeroeightsix.fiber.ir.ConfigValue;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class PojoSettings {

    public static void applyToIR(ConfigNode mergeTo, Object pojo) throws IllegalAccessException {
        ConfigNode node = parsePojo(pojo);
        ConfigOperations.mergeTo(node, mergeTo);
    }

    private static ConfigNode parsePojo(Object pojo) throws IllegalAccessException {
        ConfigNode node = new ConfigNode(null);
        boolean forceFinals = true;
        SettingNamingConvention namingConvention = new NoNamingConvention();
        Class pojoClass = pojo.getClass();

        if (pojoClass.isAnnotationPresent(Settings.class)) {
            Settings settingsAnnotation = (Settings) pojoClass.getAnnotation(Settings.class);
            if (settingsAnnotation.noForceFinals()) forceFinals = false;

            try {
                namingConvention = createNamingConvention(settingsAnnotation.namingConvention());
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
                throw new IllegalAccessException("Naming convention must have an empty constructor");
            }
        }

        parsePojo(pojo, namingConvention, node, forceFinals);
        return node;
    }

    private static List<ConfigValue> parsePojo(Object pojo, SettingNamingConvention convention, ConfigNode node, boolean forceFinals) {
        final Map<String, Pair<ConfigValueBuilder, Class>> builderMap = new HashMap<>();
        final Map<String, Pair<BiConsumer, Class>> listenerMap = new HashMap<>();

        for (Field field : pojo.getClass().getDeclaredFields()) {
            FieldProperties properties = getProperties(field);
            if (properties.ignored) continue;

            // Get angry if not final
            if (forceFinals && !properties.noForceFinal && !Modifier.isFinal(field.getModifiers())) {
                throw new IllegalStateException("Field " + field.getDeclaringClass().getCanonicalName() + "#" + field.getName() + " must be final");
            }

            if (field.isAnnotationPresent(Listener.class)) {
                if (!field.getType().equals(BiConsumer.class)) {
                    throw new IllegalStateException("Field " + field.getDeclaringClass().getCanonicalName() + "#" + field.getName() + " must be a BiConsumer");
                }

                Listener annot = field.getAnnotation(Listener.class);
                String settingName = annot.value();


                ParameterizedType genericTypes = (ParameterizedType) field.getGenericType();
                if (genericTypes.getActualTypeArguments().length != 2) {
                    throw new IllegalStateException("Field " + field.getDeclaringClass().getCanonicalName() + "#" + field.getName() + " must have 2 generic types");
                } else if (genericTypes.getActualTypeArguments()[0] != genericTypes.getActualTypeArguments()[1]) {
                    throw new IllegalStateException("Field " + field.getDeclaringClass().getCanonicalName() + "#" + field.getName() + " must have 2 identical generic types");
                }
                Class genericType = (Class) genericTypes.getActualTypeArguments()[0];

                boolean isAccessible = field.isAccessible();
                field.setAccessible(true);
                BiConsumer consumer;
                try {
                    consumer = (BiConsumer) field.get(pojo);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                } finally {
                    field.setAccessible(isAccessible);
                }
                if (consumer == null) {
                    continue;
                }

                if (builderMap.containsKey(settingName)) {
                    Pair<ConfigValueBuilder, Class> builderClassPair = builderMap.get(settingName);
                    ConfigValueBuilder builder = builderClassPair.a;
                    Class clazz = builderClassPair.b;
                    if (!clazz.equals(genericType)) {
                        throw new IllegalStateException("Field " + field.getDeclaringClass().getCanonicalName() + "#" + field.getName() + " must be of type " + clazz.getCanonicalName());
                    }
                    builder.listen(consumer);
                } else {
                    listenerMap.put(settingName, new Pair<>(consumer, genericType));
                }

                continue;
            }

            // Get type
            Class type = field.getType();
            if (type.isPrimitive()) {
                type = Primitives.wrap(type); // We're dealing with boxed primitives
            }

            // Construct builder by type
            ConfigValueBuilder builder = node.builder(type)
                    .comment(properties.comment);

            // Set final if final
            if (properties.finalValue) {
                builder.setFinal();
            }

            // Get name
            String name = field.getName();
            String conventionName = convention.name(name);
            name = (conventionName == null || conventionName.isEmpty()) ? name : conventionName;
            builder.name(name);

            // Get value
            boolean isAccessible = field.isAccessible();
            field.setAccessible(true);
            try {
                Object value = field.get(pojo);
                builder.defaultValue(value);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            field.setAccessible(isAccessible);

            // Check for listeners
            if (listenerMap.containsKey(name)) {
                Pair<BiConsumer, Class> consumerClassPair = listenerMap.get(name);

                if (!consumerClassPair.b.equals(type)) {
                    throw new IllegalStateException("Field " + field.getDeclaringClass().getCanonicalName() + "#" + field.getName() +" has a listener of type " + consumerClassPair.b.getCanonicalName() + ", while it has to be of type " + type.getCanonicalName());
                }

                builder.listen(consumerClassPair.a);
            }

            builderMap.put(name, new Pair(builder, type));
        }

        return builderMap.values().stream().map(pair -> pair.a.build()).collect(Collectors.toList());
    }

    private static String getComment(Comment annotation) {
        return annotation == null ? null : annotation.value();
    }

    private static String getComment(Field field) {
        return getComment(field.getAnnotation(Comment.class));
    }

    private static SettingNamingConvention createNamingConvention(Class<? extends SettingNamingConvention> namingConvention) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        return namingConvention.getDeclaredConstructor().newInstance();
    }

    private static FieldProperties getProperties(Field field) {
        String comment = getComment(field);
        boolean ignored = field.isAnnotationPresent(Setting.Ignored.class);
        boolean noForceFinal = field.isAnnotationPresent(Setting.NoForceFinal.class);
        boolean finalValue = field.isAnnotationPresent(Setting.Final.class);
        Set<Constraint> constraints = new HashSet<>();

        return new FieldProperties(comment, ignored, noForceFinal, finalValue, constraints);
    }

    private static class FieldProperties {
        final String comment;
        final boolean ignored;
        final boolean noForceFinal;
        final boolean finalValue;
        final Set<Constraint> constraintSet;

        FieldProperties(String comment, boolean ignored, boolean noForceFinal, boolean finalValue, Set<Constraint> constraintSet) {
            this.comment = comment;
            this.ignored = ignored;
            this.noForceFinal = noForceFinal;
            this.finalValue = finalValue;
            this.constraintSet = constraintSet;
        }
    }

    private static class Pair<A, B> {
        A a;
        B b;

        public Pair(A a, B b) {
            this.a = a;
            this.b = b;
        }
    }

}