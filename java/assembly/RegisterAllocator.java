package assembly;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ast.visitor.AbstractASTVisitor;

import ast.*;
import assembly.instructions.*;
import assembly.instructions.Instruction.OpCode;
import assembly.instructions.Instruction.Operand;
import compiler.LocalScope;
import compiler.Scope;
import compiler.Scope.SymbolTableEntry;
import compiler.Scope.Type;
import compiler.Compiler;
import compiler.GlobalScope;

public class RegisterAllocator {
    private int regNum;
    private CodeObject body;
    private LocalScope scope;
    private LinkedHashMap<String, Tuple> regMap = generateHashMap();
    private InstructionList code = new InstructionList();

    public RegisterAllocator(int regNum, CodeObject body, LocalScope scope) {
        this.regNum = regNum;
        this.body = body;
        this.scope = scope;
    }

    public CodeObject run() {
        List<InstructionList> bbs = getBasicBlocks(body.code);

        for (InstructionList bb : bbs) {
            List<List<String>> livenessInfo = performLivenessAnalysis(bb);
            // for (List<String> ls : livenessInfo) {
            //     for (String s : ls) {
            //         System.err.print(s);
            //         System.err.print(" ");
            //     }
            //     System.err.println();
            // }
            regAllocation(bb, livenessInfo);
            //code.addAll(bb);
        }

        body.code = code;
        return body;
    }

    public List<String> getUsedRegs() {
        List<String> regs = new ArrayList<>();

        for (String key : regMap.keySet()) {
            Tuple pair = regMap.get(key);
            if (pair.used) {
                regs.add(key);
            }
        }
        return regs;
    }

    public List<InstructionList> getBasicBlocks(InstructionList code) {
        List<InstructionList> bbs = new ArrayList<>();
        // generate list of basic blocks
        Set<Integer> leaders = new HashSet<Integer>();
        leaders.add(1);
        for(int i = 3; i <= code.size(); i++) {
            if (((Instruction) (code.toArray()[i - 1])) instanceof Label) {
                leaders.add(i);
                continue;
            }
            switch (((Instruction) (code.toArray()[i - 1])).getOC()) {
                case BEQ:
		        case BGE:
		        case BGT:
		        case BLE:
		        case BLT:
		        case BNE:
		        case J:
                    leaders.add(i + 1);
                    break;
                default:
                    break;
            }
        }

        InstructionList bb = new InstructionList();
        leaders.remove(1);
        bb.add((Instruction) (code.toArray()[0]));
        for (int i = 2; i <= code.size(); i++) {
            if (leaders.contains(i)) {
                leaders.remove(i);
                bbs.add(bb);
                bb = new InstructionList();
            }
            bb.add((Instruction) (code.toArray()[i - 1]));
        }
        bbs.add(bb);
        return bbs;
    }

    static List<String> getVars(List<SymbolTableEntry> stes, boolean isLocal) {
        List<String> vars = new ArrayList<>();

        for (SymbolTableEntry ste : stes) {
            if (isLocal) {
                vars.add("$l" + ste.addressToString());
            }
            else {
                vars.add("$g" + ste.getName());
            }
        }

        return vars;
    }

    public List<List<String>> performLivenessAnalysis(InstructionList bb) {
        List<List<String>> livenessInfo = new ArrayList<>();
        // generate list (length of bb) of lists of strings which holds live variable info for each line
        List<SymbolTableEntry> localstes = new ArrayList<>(scope.getEntries());
        GlobalScope gscope = (GlobalScope) Compiler.symbolTable.getGlobalScope();
        List<SymbolTableEntry> globalstes = new ArrayList<>(gscope.getEntries());
        Set<String> lives = new HashSet<>();
        List<String> globalVars = getVars(globalstes, false);
        List<String> localVars = getVars(localstes, true);

        lives.addAll(globalVars);
        lives.addAll(localVars);
        
        for (int i = bb.size() - 1; i >= 0; i--) {
            Set<String> prevlives = new HashSet<>();
            Set<String> use = new HashSet<>();
            Set<String> kill = new HashSet<>();
            Instruction line = (Instruction) (bb.toArray()[i]);
            List<String> livelist = new ArrayList<>(lives);
            livenessInfo.add(livelist);
            if (line.is3AC(line.getDest())) {
                if (line.getOC() != OpCode.SW && line.getOC() != OpCode.FSW) {
                    kill.add(line.getDest());
                }
                else {
                    use.add(line.getDest());
                }
            }
            if (line.is3AC(line.getSrc1())) {
                if (line.getOC() != OpCode.SW && line.getOC() != OpCode.FSW) {
                    use.add(line.getSrc1());
                }
                else {
                    kill.add(line.getSrc1());
                }
            }
            if (line.is3AC(line.getSrc2())) {
                use.add(line.getSrc2());
            }

            prevlives.addAll(use);
            lives.removeAll(kill);
            prevlives.addAll(lives);

            lives.clear();
            lives.addAll(prevlives);
            prevlives.clear();
        }
        Collections.reverse(livenessInfo);
        return livenessInfo;
    }

    static LinkedHashMap<String, Tuple> generateHashMap() {
        LinkedHashMap<String, Tuple> hashMap = new LinkedHashMap<String, Tuple>();

        for (int i = 1; i <= 1023; i++) {
            String var = "";
            if (i == 1) {
                var = "ra";
            }
            else if (i == 2) {
                var = "sp";
            }
            else if (i == 8) {
                var = "fp";
            }
            String key = "x" + i;
            Tuple value = new Tuple(var, false, false);
            hashMap.put(key, value);
        }

        for (int i = 0; i < 1024; i++) {
            String key = "f" + i;
            Tuple value = new Tuple("", false, false);
            hashMap.put(key, value);
        }

        return hashMap;
    }

    public void free(String r, String opr, List<String> lives, Type type, SymbolTableEntry ste) {
        Tuple pair = regMap.get(r);
        String offset = "0";
        String addr = ste.addressToString();
        if (pair.isDirty && lives.contains(opr)) {
            if (ste.isLocal()) {
                offset = ste.addressToString();
                addr = "fp";
            }
            if (type == Type.FLOAT) {
                code.add(new Fsw(r, addr, offset));
            }
            else {
                code.add(new Sw(r, addr, offset));
            }
        }
        pair.var = "";
        pair.isDirty = false;
        regMap.put(r, pair);
    }

    static InstructionList rvalify(String opr, String r, Type type, SymbolTableEntry ste, List<String> lives) {
        InstructionList loads = new InstructionList();
        String offset = "0";
        String addr;
        if (ste.isLocal()) {
            addr = "fp";
            offset = ste.addressToString();
        }
        else {
            addr = ste.addressToString();
            if (type == Type.INT || type == Type.FLOAT) {
                String reg = "x3";
                loads.add(new La(reg, addr));
                addr = reg;
            }
            else {
                loads.add(new La(r, addr));
                addr = r;
            }
            
        }
        switch(type) {
            case INT: 
                loads.add(new Lw(r, addr, offset));
                break;
            case FLOAT:
                loads.add(new Flw(r, addr, offset));
                break;
            default:
                break;
        }

        return loads;
    }

    public String ensure(String opr, Type type, List<String> lives, SymbolTableEntry ste) {
        for (String key : regMap.keySet()) {
            Tuple pair = regMap.get(key);
            if (pair.var.equals(opr)) {
                return key;
            }
        }
        String r = allocate(opr, type, lives, ste);
        code.addAll(rvalify(opr, r, type, ste, lives));

        return r;
    }

    public boolean isProperType(String key, Type type) {
        if (key.startsWith("f") && type == Type.FLOAT) {
            return true;
        }
        else if (key.startsWith("x") && (type == Type.INT || type == Type.STRING)) {
            return true;
        }
        return false;
    }

    public String allocate(String opr, Type type, List<String> lives, SymbolTableEntry ste) {
        for (String key : regMap.keySet()) {
            Tuple pair = regMap.get(key);
            if (pair.var.isEmpty() && isProperType(key, type) && !key.equals("x3")) {
                pair.var = opr;
                pair.used = true;
                regMap.put(key, pair);
                return key;
            }
        }
        for (String key : regMap.keySet()) {
            Tuple pair = regMap.get(key);
            String regToFree;
            if (!pair.isDirty && isProperType(key, type) && !key.equals("x3")) {
                regToFree = key;
                // if (pair.var.startsWith("$t") || pair.var.startsWith("$f")) {
                //     spilled_temp_handler();
                // } 
                free(regToFree, pair.var, lives, type, ste);
                pair.var = opr;
                pair.used = true;
                regMap.put(regToFree, pair);
                return regToFree;
            }
        }
        return "";
    }

    public Tuple getTypeSTE(String opr, List<SymbolTableEntry> localstes, GlobalScope gscope) {
        Type type = Type.INT;
        SymbolTableEntry ste = new SymbolTableEntry(type, opr, 0);;
        if (opr.substring(0,2).equals("$l")) {
            int offset = Integer.valueOf(opr.substring(2));
            for(SymbolTableEntry s : localstes) {
                if (s.getAddress() == offset) {
                    type = s.getType();
                    ste = s;
                }
            }
        }
        else if (opr.substring(0,2).equals("$g")) {
            ste = gscope.getSymbolTableEntry(opr.substring(2));
            type = ste.getType();
        }
        else if (opr.substring(0,2).equals("$t")) {
            type = Type.INT;
            ste = new SymbolTableEntry(type, opr, 0);
        }
        else {
            type = Type.FLOAT;
            ste = new SymbolTableEntry(type, opr, 0);
        }
        Tuple retval = new Tuple(type, ste);
        return retval;
    }

    public void saveDirtyRegs(List<SymbolTableEntry> localstes, GlobalScope gscope, List<String> lives) {
        for (String key : regMap.keySet()) {
            Tuple pair = regMap.get(key);
            if (pair.isDirty) {
                Tuple typeste = getTypeSTE(pair.var, localstes, gscope);
                String addr = typeste.ste.addressToString();
                if (addr.equals("0x0")) {
                    continue;
                }
                String offset = "0";
                String reg = "x3";
                if (!typeste.ste.isLocal()) {
                    code.add(new La(reg, addr));
                    addr = reg;
                }
                else {
                    offset = addr;
                    addr = "fp";
                }
                if (typeste.type == Type.INT) {
                    code.add(new Sw(key, addr, offset));
                }
                else {
                    code.add(new Fsw(key, addr, offset));
                }
            }
        }
    }

    public void unuse() {
        for (String key : regMap.keySet()) {
            Tuple pair = regMap.get(key);
            if (pair.used) {
                pair.var = "";
                pair.isDirty = false;
                regMap.put(key, pair);
            }
        }
    }

    public void regAllocation(InstructionList bb, List<List<String>> livenessInfo) {
        // using livenessinfo and the bb code, allocate registers and expand the macros accordingly
        List<SymbolTableEntry> localstes = new ArrayList<>(scope.getEntries());
        GlobalScope gscope = (GlobalScope) Compiler.symbolTable.getGlobalScope();
        int idx = 0;
        code.add(new Blank("Start of BB"));
        for (Instruction line : bb) {
            String src1 = line.getSrc1();
            String src2 = line.getSrc2();
            String dest = line.getDest();

            Tuple src1Tuple = new Tuple(null, null);
            Tuple src2Tuple = new Tuple(null, null);
            Tuple destTuple = new Tuple(null, null);

            String Rx = "";
            String Ry = "";
            String Rz = "";

            // System.err.println(bb.toString());
            // System.err.println();
            // System.err.println(line.toString());
            // System.err.println();

            String temp = "";
            if (line.getOC() == OpCode.SW || line.getOC() == OpCode.FSW) {
                temp = src1;
                src1 = dest;
                dest = temp;
            }

            if (line.is3AC(src1)) {
                if (line.getOC() == OpCode.SW || line.getOC() == OpCode.FSW) {
                    src1Tuple = getTypeSTE(src1, localstes, gscope);
                    Rx = ensure(src1, src1Tuple.type, livenessInfo.get(idx), src1Tuple.ste);
                    if (!livenessInfo.get(idx).contains(src1)) {
                        free(Rx, src1, livenessInfo.get(idx), src1Tuple.type, src1Tuple.ste);
                    }
                    line.setDest(Rx);
                }
                else {
                    src1Tuple = getTypeSTE(src1, localstes, gscope);
                    Rx = ensure(src1, src1Tuple.type, livenessInfo.get(idx), src1Tuple.ste);
                }
            }
            if (line.is3AC(src2)) {
                src2 = line.getSrc2();
                src2Tuple = getTypeSTE(src2, localstes, gscope);
                Ry = ensure(src2, src2Tuple.type, livenessInfo.get(idx), src2Tuple.ste);
            }
            
            if (!Rx.isEmpty() && !(line.getOC() == OpCode.SW || line.getOC() == OpCode.FSW)) {
                if (!livenessInfo.get(idx).contains(src1)) {
                    free(Rx, src1, livenessInfo.get(idx), src1Tuple.type, src1Tuple.ste);
                }
                line.setSrc1(Rx);
            }
            
            if (!Ry.isEmpty()) {
                if (!livenessInfo.get(idx).contains(src2)) {
                    free(Ry, src2, livenessInfo.get(idx), src2Tuple.type, src2Tuple.ste);
                }
                line.setSrc2(Ry);
            }
            if (line.is3AC(dest)) {
                if ((line.getOC() == OpCode.SW || line.getOC() == OpCode.FSW)) {
                    destTuple = getTypeSTE(dest, localstes, gscope);
                    Rz = allocate(dest, destTuple.type, livenessInfo.get(idx), destTuple.ste);
                    line.setSrc1(Rz);
                }
                else {
                    destTuple = getTypeSTE(dest, localstes, gscope);
                    Rz = allocate(dest, destTuple.type, livenessInfo.get(idx), destTuple.ste);
                    line.setDest(Rz);
                }

                if (!Rz.isEmpty()) {
                    Tuple RzInfo = regMap.get(Rz);
                    boolean isDirty = true;
                    regMap.put(Rz, new Tuple(RzInfo.var, isDirty, true));
                }
            }

            if (line.getOC() == OpCode.SW || line.getOC() == OpCode.FSW) {
                if (Rx != Rz || (Rx.isEmpty() && Rz.isEmpty())) {
                    if (!Rx.isEmpty() && !Rz.isEmpty()) {
                        code.add(new Mv(Rx, Rz));
                    }
                    else {
                        code.add(line);
                    }
                }
            }
            else {
                code.add(line);
            }
            
            if (idx == bb.size() - 2) {
                Instruction inst = (Instruction) bb.toArray()[idx + 1];
                if(inst.getOC() != null) {
                    switch (inst.getOC()) {
                        case BEQ:
                        case BGE:
                        case BGT:
                        case BLE:
                        case BLT:
                        case BNE:
                        case J:
                            saveDirtyRegs(localstes, gscope, livenessInfo.get(idx));
                            break;
                        default:
                            break;
                    }
                }
            }
            if (idx == bb.size() - 1) {
                Instruction inst = (Instruction) bb.toArray()[idx];
                if (inst.getOC() != null) {
                    switch (inst.getOC()) {
                        case BEQ:
                        case BGE:
                        case BGT:
                        case BLE:
                        case BLT:
                        case BNE:
                        case J:
                            break;
                        default:
                            saveDirtyRegs(localstes, gscope, livenessInfo.get(idx));
                            break;
                    }
                }
                unuse();
            }
            // for (String key : regMap.keySet()) {
            //     Tuple pair = regMap.get(key);
            //     System.err.println(key + ": " + pair.var + " Dirty: " + pair.isDirty);
            // }
            idx++;
        }
        code.add(new Blank("End of BB"));

    }
    
    static class Tuple {
        String var;
        boolean isDirty;
        SymbolTableEntry ste;
        Type type;
        boolean used;

        public Tuple(String var, boolean isDirty, boolean used) {
            this.var = var;
            this.isDirty = isDirty;
            this.used = used;
        }
        public Tuple(Type type, SymbolTableEntry ste) {
            this.type = type;
            this.ste = ste;
        }

        public String toString() {
            return "(" + var + ", " + isDirty + ")";
        }
    }

    public int getRegNum() {
        return regNum;
    }

    public CodeObject getBody() {
        return body;
    }

    public LocalScope getScope() {
        return scope;
    }
}
