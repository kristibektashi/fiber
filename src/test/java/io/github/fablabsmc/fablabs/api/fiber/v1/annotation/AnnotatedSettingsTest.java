package io.github.fablabsmc.fablabs.api.fiber.v1.annotation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import io.github.fablabsmc.fablabs.api.fiber.v1.exception.FiberException;
import io.github.fablabsmc.fablabs.api.fiber.v1.schema.type.ListSerializableType;
import io.github.fablabsmc.fablabs.api.fiber.v1.schema.type.StringSerializableType;
import io.github.fablabsmc.fablabs.api.fiber.v1.schema.type.derived.ConfigTypes;
import io.github.fablabsmc.fablabs.api.fiber.v1.schema.type.derived.ListConfigType;
import io.github.fablabsmc.fablabs.api.fiber.v1.tree.ConfigLeaf;
import io.github.fablabsmc.fablabs.api.fiber.v1.tree.ConfigNode;
import io.github.fablabsmc.fablabs.api.fiber.v1.tree.ConfigTree;
import io.github.fablabsmc.fablabs.api.fiber.v1.tree.Property;
import io.github.fablabsmc.fablabs.api.fiber.v1.tree.PropertyMirror;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// TODO add tests for user-defined types and processors
@SuppressWarnings({"unused", "FieldMayBeFinal"})
class AnnotatedSettingsTest {
	private AnnotatedSettings annotatedSettings;
	private ConfigTree node;

	@BeforeEach
	void setup() {
		this.annotatedSettings = AnnotatedSettings.DEFAULT_SETTINGS;
		this.node = ConfigTree.builder().build();
	}

	@Test
	@DisplayName("Convert POJO to IR")
	void testPojoIR() throws FiberException {
		OneFieldPojo pojo = new OneFieldPojo();
		this.annotatedSettings.applyToNode(this.node, pojo);

		Collection<ConfigNode> items = this.node.getItems();
		assertEquals(1, items.size(), "Setting map is 1 entry large");
		ConfigNode item = this.node.lookup("a");
		assertNotNull(item, "Setting exists");
		assertTrue(ConfigLeaf.class.isAssignableFrom(item.getClass()), "Setting is a ConfigLeaf");
		ConfigLeaf<?> leaf = (ConfigLeaf<?>) item;
		assertNotNull(leaf.getValue(), "Setting value is non-null");
		assertEquals(ConfigTypes.INTEGER.getSerializedType(), leaf.getConfigType(), "Setting type is correct");
		assertEquals(BigDecimal.class, leaf.getValue().getClass(), "Setting value reflects correct type");
		BigDecimal decimal = (BigDecimal) leaf.getValue();
		assertEquals(decimal, BigDecimal.valueOf(5), "Setting value is correct");
		PropertyMirror<Integer> converted = PropertyMirror.create(ConfigTypes.INTEGER);
		converted.mirror(leaf);
		Integer integer = converted.getValue();
		assertEquals(integer, 5, "Setting value is correct");
	}

	@Test
	@DisplayName("Throw final exception")
	void testNoFinal() {
		FinalSettingPojo pojo = new FinalSettingPojo();
		assertThrows(FiberException.class, () -> this.annotatedSettings.applyToNode(this.node, pojo));
	}

	@Test
	@DisplayName("Listener")
	void testListener() throws FiberException {
		ListenerPojo pojo = new ListenerPojo();
		this.annotatedSettings.applyToNode(this.node, pojo);

		ConfigNode treeItem = this.node.lookup("a");
		assertNotNull(treeItem, "Setting A exists");
		assertTrue(treeItem instanceof Property, "Setting A is a property");
		PropertyMirror<Integer> property = PropertyMirror.create(ConfigTypes.INTEGER);
		property.mirror((Property<?>) treeItem);
		assertDoesNotThrow(() -> property.setValue(10), "Transient listener is not called");
		assertTrue(pojo.listenedA, "Listener for A was triggered");

		treeItem = this.node.lookup("b");
		assertNotNull(treeItem, "Setting B exists");
		assertTrue(treeItem instanceof Property, "Setting B is a property");
		property.mirror((Property<?>) treeItem);
		property.setValue(10);
		assertTrue(pojo.listenedB, "Listener for B was triggered");

		treeItem = this.node.lookup("c");
		assertNotNull(treeItem, "Setting C exists");
		assertTrue(treeItem instanceof Property, "Setting C is a property");
		property.mirror((Property<?>) treeItem);
		property.setValue(10);
		assertTrue(pojo.listenedC, "Listener for C was triggered");
	}

	@Test
	@DisplayName("Listener with different generics")
	void testTwoGenerics() {
		NonMatchingListenerPojo pojo = new NonMatchingListenerPojo();
		assertThrows(FiberException.class, () -> this.annotatedSettings.applyToNode(this.node, pojo));
	}

	@Test
	@DisplayName("Listener with wrong generic type")
	void testWrongGenerics() {
		WrongGenericListenerPojo pojo = new WrongGenericListenerPojo();
		assertThrows(FiberException.class, () -> this.annotatedSettings.applyToNode(this.node, pojo));
	}

	@Test
	@DisplayName("Numerical constraints")
	void testNumericalConstraints() throws FiberException {
		NumericalConstraintsPojo pojo = new NumericalConstraintsPojo();
		this.annotatedSettings.applyToNode(this.node, pojo);
		PropertyMirror<Integer> value = PropertyMirror.create(ConfigTypes.INTEGER);
		assertTrue(this.node.lookupAndBind("a", value));
		assertNotNull(value, "Setting exists");
		assertFalse(value.accepts(-10));
		assertTrue(value.setValue(-10));
		assertEquals(0, value.getValue());
		assertTrue(value.accepts(5));
		assertTrue(value.setValue(5));
		assertEquals(5, value.getValue());
		assertFalse(value.accepts(20));
		assertTrue(value.setValue(20));
		assertEquals(10, value.getValue());
	}

	@Test
	@DisplayName("String constraints")
	void testStringConstraints() throws FiberException {
		StringConstraintsPojo pojo = new StringConstraintsPojo();
		this.annotatedSettings.applyToNode(this.node, pojo);
		@SuppressWarnings("unchecked")
		Property<String> value = (Property<String>) this.node.lookup("a");
		assertNotNull(value, "Setting exists");
		assertFalse(value.setValue("BAD STRING::"));
		assertTrue(value.setValue("good:string"));
		assertFalse(value.setValue("b:s"), "Too short");
		assertFalse(value.setValue("bad_string:because_it_is_way_too_long"), "Too long");
	}

	@Test
	@DisplayName("Array constraints")
	void testArrayConstraints() throws FiberException {
		ArrayConstraintsPojo pojo = new ArrayConstraintsPojo();
		this.annotatedSettings.applyToNode(this.node, pojo);
		ListConfigType<String[], String> type = ConfigTypes.makeArray(ConfigTypes.STRING);
		PropertyMirror<String[]> mirror1 = PropertyMirror.create(type);
		Property<List<String>> value1 = this.node.lookupLeaf("nonEmptyArrayShortStrings", type.getSerializedType());
		assertNotNull(value1, "Setting exists");
		mirror1.mirror(value1);
		assertTrue(mirror1.setValue(new String[] {"ab", "", "ba", ""}));
		assertFalse(mirror1.setValue(new String[0]), "Empty array");
		assertFalse(mirror1.setValue(new String[] {"aaaaaaaaaaaa"}), "Strings too long");
		ListConfigType<int[], BigDecimal> type2 = ConfigTypes.makeIntArray(ConfigTypes.INTEGER);
		PropertyMirror<int[]> mirror2 = PropertyMirror.create(type2);
		this.node.lookupAndBind("numbers", mirror2);
		assertNotNull(mirror2, "Setting exists");
		assertTrue(mirror2.setValue(new int[] {3, 4, 5}));
		assertArrayEquals(new int[] {3, 4, 5}, mirror2.getValue());
		assertTrue(mirror2.setValue(new int[0]));
		assertArrayEquals(new int[0], mirror2.getValue(), "Value should not change after unrecoverable setValue");
		assertFalse(mirror2.accepts(new int[] {1, 2, 3, 4, 5, 6, 7}), "Too many elements");
		assertTrue(mirror2.setValue(new int[] {1, 2, 3, 4, 5, 6, 7}), "Recoverable length issue");
		assertArrayEquals(new int[] {1, 2, 3}, mirror2.getValue(), "Value not properly trimmed");
		assertFalse(mirror2.accepts(new int[] {1, 11, 3, 4, 5, 6, 7}), "Too many elements and element out of range");
		assertTrue(mirror2.setValue(new int[] {1, 11, 3, 4, 5, 6, 7}), "Recoverable length issue");
		assertArrayEquals(new int[] {1, 10, 3}, mirror2.getValue(), "Value not properly trimmed or corrected");
		assertFalse(mirror2.accepts(new int[] {-1, 0, 1}), "Negative number not allowed");
		assertTrue(mirror2.setValue(new int[] {-1, 0, 1}), "Correction for out of bounds numbers available");
		assertArrayEquals(new int[] {0, 0, 1}, mirror2.getValue(), "Negative number should be brought back into range");
		assertFalse(mirror2.accepts(new int[] {9, 10, 11}), "Numbers above 10 not allowed");
		assertTrue(mirror2.setValue(new int[] {9, 10, 11}), "Correction for out of bounds numbers available");
		assertArrayEquals(new int[] {9, 10, 10}, mirror2.getValue(), ">10 number should be brought back into range");
		Property<List<String>> value3 = this.node.lookupLeaf("shortArrayIdStrings", new ListSerializableType<>(StringSerializableType.DEFAULT_STRING));
		assertNotNull(value3, "Setting exists");
		assertTrue(value3.accepts(Arrays.asList("a:b", "fabric:test")));
		assertTrue(value3.setValue(Arrays.asList("a:b", "fabric:test")));
		assertTrue(value3.accepts(Collections.emptyList()));
		assertTrue(value3.setValue(Collections.emptyList()));
		assertFalse(value3.accepts(Arrays.asList("a:b", "b:c", "c:d", "d:e")), "Too many elements");
		assertTrue(value3.setValue(Arrays.asList("a:b", "b:c", "c:d", "d:e")), "Too many elements");
		assertEquals(Arrays.asList("a:b", "b:c", "c:d"), value3.getValue());
		assertFalse(value3.accepts(Collections.singletonList("aaaaaaaaaaaa")), "Bad regex");
		assertTrue(value3.setValue(Collections.singletonList("aaaaaaaaaaaa")), "Bad regex");
		assertTrue(value3.getValue().isEmpty());
		assertFalse(value3.accepts(Arrays.asList("do", "do", "do", "while:true")), "Bad regex");
		assertTrue(value3.setValue(Arrays.asList("do", "do", "do", "while:true")), "Bad regex");
		assertEquals(Collections.singletonList("while:true"), value3.getValue());
	}

	@Test
	@DisplayName("Invalid constraints")
	void testInvalidConstraints() {
		assertThrows(FiberException.class, () -> this.annotatedSettings.makeTree(new InvalidConstraintPojo()));
	}

	@Test
	@DisplayName("Only annotated fields")
	void testOnlyAnnotatedFields() throws FiberException {
		OnlyAnnotatedFieldsPojo pojo = new OnlyAnnotatedFieldsPojo();
		AnnotatedSettings.builder().collectOnlyAnnotatedMembers().build().applyToNode(this.node, pojo);
		assertEquals(1, this.node.getItems().size(), "Node has one item");
	}

	@Test
	@DisplayName("Custom named setting")
	void testCustomNames() throws FiberException {
		CustomNamePojo pojo = new CustomNamePojo();
		this.annotatedSettings.applyToNode(this.node, pojo);
		assertNotNull(this.node.lookup("custom_name"), "Custom named setting exists");
	}

	@Test
	@DisplayName("Subnodes")
	void testSubNodes() throws FiberException {
		SubNodePojo pojo = new SubNodePojo();
		this.annotatedSettings.applyToNode(this.node, pojo);
		assertEquals(1, this.node.getItems().size(), "Node has one item");
		ConfigTree subnode = (ConfigTree) this.node.lookup("a");
		assertNotNull(subnode, "Subnode exists");
		assertEquals(1, subnode.getItems().size(), "Subnode has one item");
	}

	@Test
	@DisplayName("Enum")
	void testEnum() throws FiberException {
		EnumPojo pojo = new EnumPojo();
		this.annotatedSettings.applyToNode(this.node, pojo);
		assertEquals(1, this.node.getItems().size(), "Node has one item");
		ConfigNode child = this.node.lookup("a");
		assertTrue(child instanceof ConfigLeaf);
		ConfigLeaf<?> leaf = (ConfigLeaf<?>) child;
		assertEquals(ConfigTypes.makeEnum(EnumPojo.TestEnum.class).getSerializedType(), leaf.getConfigType());
		assertEquals("A", leaf.getValue());
	}

	@Test
	@DisplayName("Commented setting")
	void testComment() throws FiberException {
		CommentPojo pojo = new CommentPojo();
		this.annotatedSettings.applyToNode(this.node, pojo);
		assertEquals("comment", ((ConfigLeaf<?>) Objects.requireNonNull(this.node.lookup("a"))).getComment(), "Comment exists and is correct");
	}

	@Test
	@DisplayName("Ignored settings")
	void testIgnore() throws FiberException {
		IgnoredPojo pojo = new IgnoredPojo();
		this.annotatedSettings.applyToNode(this.node, pojo);
		assertEquals(0, this.node.getItems().size(), "Node is empty");
	}

	@Test
	@DisplayName("Null default value")
	void testNullDefault() {
		Object pojo = new NullDefaultPojo();
		assertThrows(FiberException.class,
				() -> this.annotatedSettings.applyToNode(this.node, pojo),
				"Prohibit null default value");
	}

	private static class NullDefaultPojo {
		private String a = null;
	}

	@Test
	@DisplayName("POJO with superclass")
	void testSuperPojo() {
		AnnotatedSettings settings = AnnotatedSettings.builder().collectMembersRecursively().build();
		assertDoesNotThrow(() -> settings.applyToNode(this.node, new ExtendingPojo()));
		assertEquals(2, this.node.getItems().size(), "Node has two items");
	}

	@Test
	@DisplayName("Directly mutated POJO")
	void lateChangePojo() throws FiberException {
		LateChangePojo pojo = new LateChangePojo();
		this.annotatedSettings.applyToNode(this.node, pojo);
		ConfigLeaf<BigDecimal> a = this.node.lookupLeaf("a", ConfigTypes.INTEGER.getSerializedType());
		pojo.a = 10;
		assertEquals(10, a.getValue().intValue());
	}

	private static class FinalSettingPojo {
		private final int a = 5;
	}

	private static class OneFieldPojo {
		private int a = 5;
	}

	private static class ListenerPojo {
		private transient boolean listenedA = false;
		private transient boolean listenedB = false;
		private transient boolean listenedC = false;

		private int a = 5;
		private int b = 5;
		private int c = 5;

		@Listener("a")
		private BiConsumer<Integer, Integer> aListener = (now, then) -> this.listenedA = true;

		@Listener("a")
		private transient BiConsumer<Integer, Integer> aTransientListener = (now, then) -> {
			throw new IllegalStateException("This listener shouldn't be called!");
		};

		@Listener("b")
		private void bListener(Integer oldValue, Integer newValue) {
			this.listenedB = true;
		}

		@Listener("c")
		private void cListener(Integer newValue) {
			this.listenedC = true;
		}
	}

	private static class NonMatchingListenerPojo {
		private int a = 5;

		@Listener("a")
		private BiConsumer<Double, Integer> aListener = (now, then) -> {
		};
	}

	private static class WrongGenericListenerPojo {
		private int a = 5;

		@Listener("a")
		private BiConsumer<Double, Double> aListener = (now, then) -> {
		};
	}

	private static class NumericalConstraintsPojo {
		@Setting.Constrain.Range(min = 0, max = 10)
		private int a = 5;
	}

	private static class StringConstraintsPojo {
		@Setting.Constrain.MinLength(5)
		@Setting.Constrain.MaxLength(20)
		@Setting.Constrain.Regex("[a-z0-9_.-]{2,}:[a-z0-9_./-]+?")
		private String a = "fabric:test";
	}

	private static class ArrayConstraintsPojo {
		private
		@Setting.Constrain.MaxLength(2)
		String
		@Setting.Constrain.MinLength(1) [] nonEmptyArrayShortStrings = {""};

		private
		@Setting.Constrain.Range(min = 0, max = 10)
		int
		@Setting.Constrain.MinLength(0) @Setting.Constrain.MaxLength(3) [] numbers = {};

		private @Setting.Constrain.MaxLength(3) List<@Setting.Constrain.Regex("\\w+:\\w+") String> shortArrayIdStrings = Collections.singletonList("fabric:test");
	}

	private static class InvalidConstraintPojo {
		private @Setting.Constrain.Regex("\\d") int i;
	}

	private static class OnlyAnnotatedFieldsPojo {
		@Setting
		private int a = 5;

		private int b = 6;
	}

	private static class CustomNamePojo {
		@Setting(name = "custom_name")
		private int a = 5;
	}

	private static class CommentPojo {
		@Setting(comment = "comment")
		private int a = 5;
	}

	private static class IgnoredPojo {
		@Setting(ignore = true)
		private int a = 5;

		private transient int b = 5;
	}

	private static class SubNodePojo {
		@Setting.Group(name = "a")
		public SubNode node = new SubNode();

		// we want to test this edge case
		@SuppressWarnings("InnerClassMayBeStatic")
		class SubNode {
			private int b = 5;
		}
	}

	private static class EnumPojo {
		public TestEnum a = TestEnum.A;

		enum TestEnum {
			A {
				@Override
				public String toString() {
					return "hi";
				}
			}, B, C
		}
	}

	@Settings(onlyAnnotated = true)
	private static class SuperPojo {
		@Setting
		private int a = 5;

		private int ignored = 10;
	}

	private static class ExtendingPojo extends SuperPojo {
		private int b = 5;
	}

	private static class LateChangePojo {
		private int a = 5;
	}
}
