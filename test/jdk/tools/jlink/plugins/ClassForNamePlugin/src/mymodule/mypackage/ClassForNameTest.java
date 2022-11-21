/*
 * Copyright (c) 2022 Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package mypackage;

public class ClassForNameTest {

    public static void main(String[] args) {
        simpleTryCatchRemovalTest();
        jointExceptionTest();
        multipleCatchBlockTest();
        simpleTryCatchFinallyRemovalTest();
        nestedAllCallsTransformedTest();
        nestedAllFinallyCallsTransformedTest();
        nestedSomeCallsTransformedTest();
        preserveExceptionTest();
    }

    public static void simpleTryCatchRemovalTest() {
        try {
            Class.forName("mypackage.ClassForNameTest");
        } catch (ClassNotFoundException e) {
            System.out.println("Class not found");
        }
    }

    public static void jointExceptionTest() {
        try {
            String x = null;
            Class.forName("mypackage.ClassForNameTest");
            System.out.println(x.equals("x"));
        } catch (ClassNotFoundException | NullPointerException e) {
            System.out.println("Joint exception failure");
        }
    }

    public static void multipleCatchBlockTest() {
        try {
            String x = null;
            Class.forName("mypackage.ClassForNameTest");
            System.out.println(x.equals("x"));
        } catch (ClassNotFoundException e1) {
            System.out.println("Class not found");
        } catch (NullPointerException e2) {
            System.out.println("Null pointer");
        }
    }

    public static void simpleTryCatchFinallyRemovalTest() {
        try {
            Class.forName("mypackage.ClassForNameTest");
        } catch (ClassNotFoundException e) {
            System.out.println("Class not found");
        } finally {
            System.out.println("finally!");
        }
    }

    public static void nestedAllCallsTransformedTest() {
        try {
            Class.forName("mypackage.ClassForNameTest");
            try {
                Class.forName("mypackage.ClassForNameTest");
            } catch (ClassNotFoundException e) {
                System.out.println("inner handler");
            }
        } catch (ClassNotFoundException e) {
            System.out.println("outer handler");
        }
    }

    public static void nestedAllFinallyCallsTransformedTest() {
        try {
            Class.forName("mypackage.ClassForNameTest");
            try {
                Class.forName("mypackage.ClassForNameTest");
            } catch (ClassNotFoundException e) {
                System.out.println("Inner catch block!");
            } finally {
                System.out.println("inner finally!");
            }
        } catch (ClassNotFoundException e) {
            System.out.println("Catch block");
        } finally {
            System.out.println("outer finally!");
        }
    }

    public static void nestedSomeCallsTransformedTest() {
        try {
            Class.forName("mypackage.ClassForNameTest"); // will transform
            try {
                Class.forName("Random!"); // won't transform
            } catch (ClassNotFoundException e) {
                System.out.println("Random class not found!");
            }
        } catch (ClassNotFoundException e) {
            System.out.println("outer");
        }
    }


    public static void preserveExceptionTest() {
        try {
            Class.forName("mypackage.ClassForNameTest");
            Class.forName("someRandomClass");
        } catch (ClassNotFoundException e) {
            System.out.println("Random class not found!");
        }
    }
}
