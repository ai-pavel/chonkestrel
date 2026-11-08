package xyz.znix.xftl.bxml;

import org.jdom2.*;
import org.jdom2.internal.ArrayCopy;
import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

import java.lang.reflect.InaccessibleObjectException;
import java.util.AbstractList;

/**
 * Attribute objects are surprisingly expensive to create, as they always
 * validate their name/value/type/namespace.
 * <p>
 * We know they're valid since otherwise we couldn't have serialised them,
 * so defeat those checks if we can.
 * <p>
 * This uses Unsafe to create a blank object instance, so fallback to creating
 * them normally if that's not available.
 * <p>
 * The same applies for creating elements (name validation), adding attributes
 * (checking for duplicates), and adding content (checking for parent loops).
 * <p>
 * This is written in Java instead of Kotlin, since everything needs
 * to be final static fields to allow for proper inlining.
 */
public final class UnsafeXML {
    private UnsafeXML() {
    }

    // Making this static allows constant folding
    private static final Unsafe unsafe;

    static {
        Unsafe theUnsafe = null;
        try {
            var field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            theUnsafe = (Unsafe) field.get(null);
        } catch (InaccessibleObjectException | ReflectiveOperationException ex) {
            System.out.println("Cannot access sun.misc.Unsafe, BXML deserialisation will be slightly slower.");
        }
        unsafe = theUnsafe;
    }

    private static final long offsetAttrSpecified = lookupOffset(Attribute.class, "specified");
    private static final long offsetAttrName = lookupOffset(Attribute.class, "name");
    private static final long offsetAttrValue = lookupOffset(Attribute.class, "value");
    private static final long offsetAttrType = lookupOffset(Attribute.class, "type");
    private static final long offsetAttrNamespace = lookupOffset(Attribute.class, "namespace");
    private static final long offsetAttrParent = lookupOffset(Attribute.class, "parent");

    private static final long offsetElemName = lookupOffset(Element.class, "name");
    private static final long offsetElemNamespace = lookupOffset(Element.class, "namespace");
    private static final long offsetElemAttributes = lookupOffset(Element.class, "attributes");

    private static final Class<?> attributeList = wrapInit(() -> Class.forName("org.jdom2.AttributeList"));
    private static final long offsetAttributeListAttributes = lookupOffset(attributeList, "attributeData");
    private static final long offsetAttributeListSize = lookupOffset(attributeList, "size");

    private static final int ATTRIBUTE_LIST_INITIAL_ARRAY_SIZE = 4;

    private static long lookupOffset(Class<?> type, String name) {
        if (unsafe == null)
            return 0;
        try {
            return unsafe.objectFieldOffset(type.getDeclaredField(name));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    public static Attribute createAttribute(String name, String value) {
        if (unsafe == null) {
            return new Attribute(name, value);
        }

        // Create an instance without running the constructor.
        // You're not at all supposed to be able to do this, and if JDOM
        // changes its internals it's our fault if this breaks (though that's
        // not terribly likely).
        Attribute attr;
        try {
            attr = (Attribute) unsafe.allocateInstance(Attribute.class);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }

        unsafe.putBoolean(attr, offsetAttrSpecified, true);
        unsafe.putObject(attr, offsetAttrName, name);
        unsafe.putObject(attr, offsetAttrValue, value);
        unsafe.putObject(attr, offsetAttrType, AttributeType.UNDECLARED);
        unsafe.putObject(attr, offsetAttrNamespace, Namespace.NO_NAMESPACE);

        return attr;
    }

    @NotNull
    public static Element createElement(String name) {
        if (unsafe == null) {
            return new Element(name);
        }

        // Run the basic constructor, it doesn't do anything slow.
        Element elem = JDOMAccessXFTL.newElement();

        unsafe.putObject(elem, offsetElemName, name);
        unsafe.putObject(elem, offsetElemNamespace, Namespace.NO_NAMESPACE);

        return elem;
    }

    public static void addAttribute(Element elem, Attribute attr) {
        if (unsafe == null) {
            elem.setAttribute(attr);
            return;
        }

        unsafe.putObject(attr, offsetAttrParent, elem);

        // Get or create the element's attribute list
        var attributes = (AbstractList<?>) unsafe.getObject(elem, offsetElemAttributes);
        if (attributes == null) {
            attributes = JDOMAccessXFTL.newAttributeList(elem);
            unsafe.putObject(elem, offsetElemAttributes, attributes);
        }

        // Grow the backing array
        int oldSize = unsafe.getInt(attributes, offsetAttributeListSize);
        unsafe.putInt(attributes, offsetAttributeListSize, oldSize + 1);

        Attribute[] attributeData = (Attribute[]) unsafe.getObject(attributes, offsetAttributeListAttributes);

        // Copied from AttributeList
        int minCapacity = oldSize + 1;
        if (attributeData == null) {
            attributeData = new Attribute[Math.max(minCapacity, ATTRIBUTE_LIST_INITIAL_ARRAY_SIZE)];
            unsafe.putObject(attributes, offsetAttributeListAttributes, attributeData);
        } else if (minCapacity >= attributeData.length) {
            attributeData = ArrayCopy.copyOf(attributeData, minCapacity + ATTRIBUTE_LIST_INITIAL_ARRAY_SIZE);
            unsafe.putObject(attributes, offsetAttributeListAttributes, attributeData);
        }

        // Put the attribute into the array
        attributeData[oldSize] = attr;
    }

    /**
     * A wrapper function to easily setup stuff in static final fields.
     */
    private static <T> T wrapInit(InitFn<T> function) {
        try {
            return function.run();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private interface InitFn<T> {
        T run() throws Throwable;
    }
}
