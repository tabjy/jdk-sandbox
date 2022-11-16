/*
 * Copyright c 2007, 2011 Eric Bruneton
 *
 * All rights reserved.
 *
 * Redistribution and use in source (LYX format) and compiled forms (LATEX,PDF, PostScript, HTML, RTF, etc),
 * with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code (LYX format) must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 2. Redistributions in compiled form (converted to LATEX, PDF, PostScript,HTML, RTF, and other formats)
 * must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products derived from this documentation
 * without specific prior written permission.
 *
 * THIS DOCUMENTATION IS PROVIDED BY THE AUTHOR "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN
 * NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS DOCUMENTATION,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package jdk.tools.jlink.internal;

import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.LabelNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import jdk.internal.org.objectweb.asm.tree.analysis.*;

public class RemoveDeadCodeAdapter extends MethodVisitor {
    String owner;
    MethodVisitor next;
    public RemoveDeadCodeAdapter(String owner, int access, String name,
                                 String desc, MethodVisitor mv) {
        super(Opcodes.ASM9, new MethodNode(access, name, desc, null, null));
        this.owner = owner;
        next = mv;
    }
    @Override
    public void visitEnd() {
        MethodNode mn = (MethodNode) mv;
        Analyzer<BasicValue> a = new Analyzer<>(new BasicInterpreter());
        try {
            a.analyze(owner, mn);
            Frame<BasicValue>[] frames = a.getFrames();
            AbstractInsnNode[] insns = mn.instructions.toArray();
            for (int i = 0; i < frames.length; ++i) {
                if (frames[i] == null && !(insns[i] instanceof LabelNode)) {
                    mn.instructions.remove(insns[i]);
                }
            }
        } catch (AnalyzerException e) {
        }
        mn.accept(next);
    }
}