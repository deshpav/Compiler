package assembly;

import java.util.ArrayList;
import java.util.List;

import ast.visitor.AbstractASTVisitor;

import ast.*;
import assembly.instructions.*;
import compiler.Scope;
import compiler.Scope.SymbolTableEntry;

public class CodeGenerator extends AbstractASTVisitor<CodeObject> {

	int intRegCount;
	int floatRegCount;
	static final public String intTempPrefix = "$t";
	static final public String floatTempPrefix = "$f";
	
	int loopLabel;
	int elseLabel;
	int outLabel;

	static public int numIntRegisters = 2048;
	static public int numFloatRegisters = 2048;

	String currFunc;
	
	public CodeGenerator() {
		loopLabel = 0;
		elseLabel = 0;
		outLabel = 0;
		intRegCount = 0;		
		floatRegCount = 0;
	}

	public int getIntRegCount() {
		return intRegCount;
	}

	public int getFloatRegCount() {
		return floatRegCount;
	}
	
	/**
	 * Generate code for Variables
	 * 
	 * Create a code object that just holds a variable
	 * 
	 * NOTE THAT THIS HAS CHANGED TO GENERATE 3AC INSTEAD
	 */
	@Override
	protected CodeObject postprocess(VarNode node) {
		
		Scope.SymbolTableEntry sym = node.getSymbol();
		
		CodeObject co = new CodeObject(sym);
		co.lval = true;
		co.type = node.getType();
		if (sym.isLocal()) {
			co.temp = "$l" + String.valueOf(sym.getAddress());
		} else {
			co.temp = "$g" + sym.getName();
		}


		return co;
	}

	/** Generate code for IntLiterals
	 * 
	 * NOTE THAT THIS HAS CHANGED TO GENERATE 3AC INSTEAD
	 */
	@Override
	protected CodeObject postprocess(IntLitNode node) {
		CodeObject co = new CodeObject();
		
		//Load an immediate into a register
		//The li and la instructions are the same, but it's helpful to distinguish
		//for readability purposes.
		//li tmp' value
		Instruction i = new Li(generateTemp(Scope.Type.INT), node.getVal());

		co.code.add(i); //add this instruction to the code object
		co.lval = false; //co holds an rval -- data
		co.temp = i.getDest(); //temp is in destination of li
		co.type = node.getType();

		return co;
	}

	/** Generate code for FloatLiteras
	 * 
	 * NOTE THAT THIS HAS CHANGED TO GENERATE 3AC INSTEAD
	 */
	@Override
	protected CodeObject postprocess(FloatLitNode node) {
		CodeObject co = new CodeObject();
		
		//Load an immediate into a regisster
		//The li and la instructions are the same, but it's helpful to distinguish
		//for readability purposes.
		//li tmp' value
		Instruction i = new FImm(generateTemp(Scope.Type.FLOAT), node.getVal());

		co.code.add(i); //add this instruction to the code object
		co.lval = false; //co holds an rval -- data
		co.temp = i.getDest(); //temp is in destination of li
		co.type = node.getType();

		return co;
	}

	/**
	 * Generate code for binary operations.
	 * 
	 * Step 0: create new code object
	 * Step 1: add code from left child
	 * Step 1a: if left child is an lval, add a load to get the data
	 * Step 2: add code from right child
	 * Step 2a: if right child is an lval, add a load to get the data
	 * Step 3: generate binary operation using temps from left and right
	 * 
	 * Don't forget to update the temp and lval fields of the code object!
	 * 	   Hint: where is the result stored? Is this data or an address?
	 * 
	 */
	@Override
	protected CodeObject postprocess(BinaryOpNode node, CodeObject left, CodeObject right) {

		CodeObject co = new CodeObject();
		
		/* FILL IN FROM STEP 2 */
		// if (left.lval) {
		// 	left = rvalify(left);
		// }
		co.code.addAll(left.code);

		// if (right.lval) {
		// 	right = rvalify(right);
		// }
		co.code.addAll(right.code);

		Instruction binaryOp;
		if (node.getType() == Scope.Type.INT) {
			switch (node.getOp()) {
				case ADD:
					binaryOp = new Add(left.temp, right.temp, generateTemp(Scope.Type.INT));
					break;
				case SUB:
					binaryOp = new Sub(left.temp, right.temp, generateTemp(Scope.Type.INT));
					break;
				case MUL:
					binaryOp = new Mul(left.temp, right.temp, generateTemp(Scope.Type.INT));
					break;
				case DIV:
					binaryOp = new Div(left.temp, right.temp, generateTemp(Scope.Type.INT));
					break;
				default:
					binaryOp = null;
					break;
			}
		}
		else{
			switch (node.getOp()) {
				case ADD:
					binaryOp = new FAdd(left.temp, right.temp, generateTemp(Scope.Type.FLOAT));
					break;
				case SUB:
					binaryOp = new FSub(left.temp, right.temp, generateTemp(Scope.Type.FLOAT));
					break;
				case MUL:
					binaryOp = new FMul(left.temp, right.temp, generateTemp(Scope.Type.FLOAT));
					break;
				case DIV:
					binaryOp = new FDiv(left.temp, right.temp, generateTemp(Scope.Type.FLOAT));
					break;
				default:
					binaryOp = null;
					break;
			}

		}

    	co.code.add(binaryOp);

		co.temp = binaryOp.getDest();
    	co.lval = false;
    	co.type = node.getType();
		/* MODIFY THIS TO GENERATE 3AC INSTEAD */

		return co;
	}

	/**
	 * Generate code for unary operations.
	 * 
	 * Step 0: create new code object
	 * Step 1: add code from child expression
	 * Step 2: generate instruction to perform unary operation
	 * 
	 * Don't forget to update the temp and lval fields of the code object!
	 * 	   Hint: where is the result stored? Is this data or an address?
	 * 
	 */
	@Override
	protected CodeObject postprocess(UnaryOpNode node, CodeObject expr) {
		
		CodeObject co = new CodeObject();

		/* FILL IN FROM STEP 2 */
		// if (expr.lval) {
		// 	expr = rvalify(expr);
		co.code.addAll(expr.code);
		// }
		
		Instruction unaryOp = null;
		if (node.getOp() == UnaryOpNode.OpType.NEG){
			if(node.getType() == Scope.Type.FLOAT) {
				unaryOp = new FNeg(expr.temp, generateTemp(Scope.Type.FLOAT));
			}
			else{
				unaryOp = new Neg(expr.temp, generateTemp(Scope.Type.INT));
			}
		}
		co.code.add(unaryOp);

		// Update the temp and lval fields
		co.temp = unaryOp.getDest();
		co.lval = false;
		co.type = node.getType();
		/* MODIFY THIS TO GENERATE 3AC INSTEAD */

		return co;
	}

	/**
	 * Generate code for assignment statements
	 * 
	 * Step 0: create new code object
	 * Step 1: if LHS is a variable, generate a load instruction to get the address into a register
	 * Step 1a: add code from LHS of assignment (make sure it results in an lval!)
	 * Step 2: add code from RHS of assignment
	 * Step 2a: if right child is an lval, add a load to get the data
	 * Step 3: generate store
	 * 
	 * Hint: it is going to be easiest to just generate a store with a 0 immediate
	 * offset, and the complete store address in a register:
	 * 
	 * sw rhs 0(lhs)
	 */
	@Override
	protected CodeObject postprocess(AssignNode node, CodeObject left,
			CodeObject right) {
		
		CodeObject co = new CodeObject();

		/* FILL IN FROM STEP 2 */

		String offset = "0";
		// if (left.isVar()) {
		// 	if (left.getSTE().isLocal()) {
		// 		left.temp = "fp";
		// 		offset = left.getSTE().addressToString();
		// 		left.lval = false;
		// 		left.type = left.getSTE().getType();
		// 	}
		// 	else {
		// 		InstructionList il = generateAddrFromVariable(left);
		// 		co.code.addAll(il);
		// 		left.temp = il.getLast().getDest();
		// 		left.type = left.getSTE().getType();
		// 		left.lval = false;
		// 	}
		// }
		// assert(co.lval == true);

		// if (right.lval) {
		// 	right = rvalify(right);
		// }		
		co.code.addAll(left.code);
		co.code.addAll(right.code);
		
		Instruction store;
		if (node.getType() == Scope.Type.FLOAT){
			store = new Fsw(right.temp, left.temp, offset);
		}
		else{
			store = new Sw(right.temp, left.temp, offset);
		}

		co.code.add(store);
		co.temp = store.getDest();
		co.lval = false;
		co.type = node.getType();

		/* MODIFY THIS TO GENERATE 3AC INSTEAD */
		
		return co;
	}

	/**
	 * Add together all the lists of instructions generated by the children
	 */
	@Override
	protected CodeObject postprocess(StatementListNode node,
			List<CodeObject> statements) {
		CodeObject co = new CodeObject();
		//add the code from each individual statement
		for (CodeObject subcode : statements) {
			co.code.addAll(subcode.code);
		}
		co.type = null; //set to null to trigger errors
		return co;
	}
	
	/**
	 * Generate code for read
	 * 
	 * Step 0: create new code object
	 * Step 1: add code from VarNode (make sure it's an lval)
	 * Step 2: generate GetI instruction, storing into temp
	 * Step 3: generate store, to store temp in variable
	 * 
	 * NOTE THAT THIS HAS CHANGED TO GENERATE 3AC INSTEAD
	 */
	@Override
	protected CodeObject postprocess(ReadNode node, CodeObject var) {
		
		//Step 0
		CodeObject co = new CodeObject();

		//Generating code for read(id)
		assert(var.getSTE() != null); //var had better be a variable

		InstructionList il = new InstructionList();
		switch(node.getType()) {
			case INT: 
				//Code to generate if INT:
				//geti var.tmp
				Instruction geti = new GetI(var.temp);
				il.add(geti);
				break;
			case FLOAT:
				//Code to generate if FLOAT:
				//getf var.tmp
				Instruction getf = new GetF(var.temp);
				il.add(getf);
				break;
			default:
				throw new Error("Shouldn't read into other variable");
		}
		
		co.code.addAll(il);

		co.lval = false; //doesn't matter
		co.temp = null; //set to null to trigger errors
		co.type = null; //set to null to trigger errors

		return co;
	}

	/**
	 * Generate code for print
	 * 
	 * Step 0: create new code object
	 * 
	 * If printing a string:
	 * Step 1: add code from expression to be printed (make sure it's an lval)
	 * Step 2: generate a PutS instruction printing the result of the expression
	 * 
	 * If printing an integer:
	 * Step 1: add code from the expression to be printed
	 * Step 1a: if it's an lval, generate a load to get the data
	 * Step 2: Generate PutI that prints the temporary holding the expression
	 * 
	 * NOTE THAT THIS HAS CHANGED TO GENERATE 3AC INSTEAD
	 */
	@Override
	protected CodeObject postprocess(WriteNode node, CodeObject expr) {
		CodeObject co = new CodeObject();

		//generating code for write(expr)

		//for strings, we expect a variable
		if (node.getWriteExpr().getType() == Scope.Type.STRING) {
			//Step 1:
			assert(expr.getSTE() != null);

			//Step 2:
			Instruction write = new PutS(expr.temp);
			co.code.add(write);
		} else {			
			//Step 1:
			co.code.addAll(expr.code);

			//Step 2:
			//if type of writenode is int, use puti, if float, use putf
			Instruction write = null;
			switch(node.getWriteExpr().getType()) {
			case STRING: throw new Error("Shouldn't have a STRING here");
			case INT: write = new PutI(expr.temp); break;
			case FLOAT: write = new PutF(expr.temp); break;
			default: throw new Error("WriteNode has a weird type");
			}

			co.code.add(write);
		}

		co.lval = false; //doesn't matter
		co.temp = null; //set to null to trigger errors
		co.type = null; //set to null to trigger errors

		return co;
	}

	/**
	 * FILL IN FROM STEP 3
	 * 
	 * Generating an instruction sequence for a conditional expression
	 * 
	 * Implement this however you like. One suggestion:
	 *
	 * Create the code for the left and right side of the conditional, but defer
	 * generating the branch until you process IfStatementNode or WhileNode (since you
	 * do not know the labels yet). Modify CodeObject so you can save the necessary
	 * information to generate the branch instruction in IfStatementNode or WhileNode
	 * 
	 * Alternate idea 1:
	 * 
	 * Don't do anything as part of CodeGenerator. Create a new visitor class
	 * that you invoke *within* your processing of IfStatementNode or WhileNode
	 * 
	 * Alternate idea 2:
	 * 
	 * Create the branch instruction in this function, then tweak it as necessary in
	 * IfStatementNode or WhileNode
	 * 
	 * Hint: you may need to preserve extra information in the returned CodeObject to
	 * make sure you know the type of branch code to generate (int vs float)
	 */
	@Override
	protected CodeObject postprocess(CondNode node, CodeObject left, CodeObject right) {
		CodeObject co = new CodeObject();

		/* FILL IN FROM STEP 3*/
		// if (left.lval) {
		// 	left = rvalify(left);
		// }
		co.code.addAll(left.getCode());
		
		// if (right.lval) {
		// 	right = rvalify(right);
		// }
		co.code.addAll(right.getCode());
		
		co.temp = left.temp + " " + right.temp;
		co.lval = false;
		co.type = left.getType();
		/* MODIFY THIS TO GENERATE 3AC */

		return co;
	}

	/**
	 * FILL IN FROM STEP 3
	 * 
	 * Step 0: Create code object
	 * 
	 * Step 1: generate labels
	 * 
	 * Step 2: add code from conditional expression
	 * 
	 * Step 3: create branch statement (if not created as part of step 2)
	 * 			don't forget to generate correct branch based on type
	 * 
	 * Step 4: generate code
	 * 		<cond code>
	 *		<flipped branch> elseLabel
	 *		<then code>
	 *		j outLabel
	 *		elseLabel:
	 *		<else code>
	 *		outLabel:
	 *
	 * Step 5 insert code into code object in appropriate order.
	 * 
	 */
	@Override
	protected CodeObject postprocess(IfStatementNode node, CodeObject cond, CodeObject tlist, CodeObject elist) {
		//Step 0:
		CodeObject co = new CodeObject();

		/* FILL IN FROM STEP 3*/
		String elseLabel = generateElseLabel();
		String outLabel = generateOutLabel();

		String[] parts = cond.temp.split(" ");
		String l_node = parts[0];
		String r_node = parts[1];

		Instruction branch = null;
		Instruction branch2 = null;
		
		if (cond.getType() == Scope.Type.INT){
			switch (node.getCondExpr().getReversedOp()) {
				case EQ:
					branch = new Beq(l_node, r_node, elseLabel);
					break;
				case NE:
					branch = new Bne(l_node, r_node, elseLabel);
					break;
				case LT:
					branch = new Blt(l_node, r_node, elseLabel);
					break;
				case LE:
					branch = new Ble(l_node, r_node, elseLabel);
					break;
				case GT:
					branch = new Bgt(l_node, r_node, elseLabel);
					break;
				case GE:
					branch = new Bge(l_node, r_node, elseLabel);
					break;			
				default:
					break;
			}
		} else if (cond.getType() == Scope.Type.FLOAT) {
			String flt_dest = generateTemp(Scope.Type.INT);
			switch (node.getCondExpr().getReversedOp()) {
				case EQ:
					branch = (new Feq(l_node, r_node, flt_dest));
					branch2 = (new Bne(flt_dest, "x0", elseLabel));
					break;
				case NE:
					branch = (new Feq(l_node, r_node, flt_dest));
					branch2 = (new Bne(flt_dest, "x0", elseLabel));
				case LT:
					branch = (new Flt(l_node, r_node, flt_dest));
					branch2 = (new Bne(flt_dest, "x0", elseLabel));
					break;
				case LE:
					branch = (new Fle(l_node, r_node, flt_dest));
					branch2 = (new Bne(flt_dest, "x0", elseLabel));
					break;
				case GT:
					branch = new Fle(r_node, l_node, flt_dest);
					branch2 = (new Bne(flt_dest, "x0", elseLabel));
					break;
				case GE:
					branch = new Flt(r_node, l_node, flt_dest);
					branch2 = (new Bne(flt_dest, "x0", elseLabel));
					break;
				default:
					break;
			}
		}
		
		// Generating code
		co.code.addAll(cond.getCode());
		co.code.add(branch); 
		if (branch2 != null){
			co.code.add(branch2);
		}
		co.code.addAll(tlist.getCode());
		co.code.add(new J(outLabel));
		co.code.add(new Label(elseLabel));
		co.code.addAll(elist.getCode()); 
		co.code.add(new Label(outLabel));
		/* MODIFY THIS TO GENERATE 3AC */

		return co;
	}

		/**
	 * FILL IN FROM STEP 3
	 * 
	 * Step 0: Create code object
	 * 
	 * Step 1: generate labels
	 * 
	 * Step 2: add code from conditional expression
	 * 
	 * Step 3: create branch statement (if not created as part of step 2)
	 * 			don't forget to generate correct branch based on type
	 * 
	 * Step 4: generate code
	 * 		loopLabel:
	 *		<cond code>
	 *		<flipped branch> outLabel
	 *		<body code>
	 *		j loopLabel
	 *		outLabel:
	 *
	 * Step 5 insert code into code object in appropriate order.
	 */
	@Override
	protected CodeObject postprocess(WhileNode node, CodeObject cond, CodeObject slist) {
		//Step 0:
		CodeObject co = new CodeObject();

		/* FILL IN FROM STEP 3*/
		String outLabel = generateOutLabel();
		String loopLabel = generateLoopLabel();

		String[] parts = cond.temp.split(" ");
		String l_node = parts[0];
		String r_node = parts[1];

		Instruction branch = null;
		Instruction branch2 = null;
		
		if (cond.getType() == Scope.Type.INT){
			switch (node.getCond().getReversedOp()) {
				case EQ:
					branch = new Beq(l_node, r_node, outLabel);
					break;
				case NE:
					branch = new Bne(l_node, r_node, outLabel);
					break;
				case LT:
					branch = new Blt(l_node, r_node, outLabel);
					break;
				case LE:
					branch = new Ble(l_node, r_node, outLabel);
					break;
				case GT:
					branch = new Bgt(l_node, r_node, outLabel);
					break;
				case GE:
					branch = new Bge(l_node, r_node, outLabel);
					break;			
				default:
					break;
			}
		} else if (cond.getType() == Scope.Type.FLOAT) {
			String flt_dest = generateTemp(Scope.Type.INT);
			switch (node.getCond().getReversedOp()) {
				case EQ:
					branch = (new Feq(l_node, r_node, flt_dest));
					branch2 = (new Bne(flt_dest, "x0", outLabel));
					break;
				case NE:
					branch = (new Feq(l_node, r_node, flt_dest));
					branch2 = (new Bne(flt_dest, "x0", outLabel));
				case LT:
					branch = (new Flt(l_node, r_node, flt_dest));
					branch2 = (new Bne(flt_dest, "x0", outLabel));
					break;
				case LE:
					branch = (new Fle(l_node, r_node, flt_dest));
					branch2 = (new Bne(flt_dest, "x0", outLabel));
					break;
				case GT:
					branch = new Fle(r_node, l_node, flt_dest);
					branch2 = (new Bne(flt_dest, "x0", outLabel));
					break;
				case GE:
					branch = new Flt(r_node, l_node, flt_dest);
					branch2 = (new Bne(flt_dest, "x0", outLabel));
					break;
				default:
					break;
			}
		}
		
		// Generating code
		co.code.add(new Label(loopLabel));
		co.code.addAll(cond.getCode());
		co.code.add(branch);
		if (branch2 != null){
			co.code.add(branch2);
		}
		co.code.addAll(slist.getCode());
		co.code.add(new J(loopLabel));
		co.code.add(new Label(outLabel));
		/* MODIFY THIS TO GENERATE 3AC */

		return co;
	}

	/**
	 * FILL IN FOR STEP 4
	 * 
	 * Generating code for returns
	 * 
	 * Step 0: Generate new code object
	 * 
	 * Step 1: Add retExpr code to code object (rvalify if necessary)
	 * 
	 * Step 2: Store result of retExpr in appropriate place on stack (fp + 8)
	 * 
	 * Step 3: Jump to out label (use @link{generateFunctionOutLabel()})
	 */
	@Override
	protected CodeObject postprocess(ReturnNode node, CodeObject retExpr) {
		CodeObject co = new CodeObject();

		/* FILL IN FROM STEP 4*/
		
		co.code.addAll(retExpr.getCode());

		if (retExpr.getType() == Scope.Type.INT) {
        	co.code.add(new Sw(retExpr.temp, "fp", "8"));
    	}
	 	else if (retExpr.getType() == Scope.Type.FLOAT) {
        	co.code.add(new Fsw(retExpr.temp, "fp", "8"));
    	}

		co.code.add(new J(generateFunctionOutLabel()));

		co.lval = false;
		co.type = retExpr.getType();
		/* MODIFY THIS TO GENERATE 3AC */

		return co;
	}

	@Override
	protected void preprocess(FunctionNode node) {
		// Generate function label information, used for other labels inside function
		currFunc = node.getFuncName();

		//reset register counts; each function uses new registers!
		intRegCount = 0;
		floatRegCount = 0;
	}

	/**
	 * FILL IN FOR STEP 4
	 * 
	 * Generate code for functions
	 * 
	 * Step 1: add the label for the beginning of the function
	 * 
	 * Step 2: manage frame  pointer
	 * 			a. Save old frame pointer
	 * 			b. Move frame pointer to point to base of activation record (current sp)
	 * 			c. Update stack pointer
	 * 
	 * Step 3: allocate new stack frame (use scope infromation from FunctionNode)
	 * 
	 * Step 4: save registers on stack (Can inspect intRegCount and floatRegCount to know what to save)
	 * 
	 * Step 5: add the code from the function body
	 * 
	 * Step 6: add post-processing code:
	 * 			a. Label for `return` statements inside function body to jump to
	 * 			b. Restore registers
	 * 			c. Deallocate stack frame (set stack pointer to frame pointer)
	 * 			d. Reset fp to old location
	 * 			e. Return from function
	 */
	@Override
	protected CodeObject postprocess(FunctionNode node, CodeObject body) {
		CodeObject co = new CodeObject();

		RegisterAllocator ra = new RegisterAllocator(numIntRegisters, body, node.getScope());
		body = ra.run();

		/* FILL IN FROM STEP 4*/
		co.code.add(new Label(generateFunctionLabel(node.getFuncName())));
		co.code.add(new Sw("fp", "sp", "0"));
		co.code.add(new Mv("sp", "fp"));
		co.code.add(new Addi("sp", "-4", "sp"));

		int totalFrameSize = node.getScope().getNumLocals() * -4;  // Assuming 4 bytes for each local variable
		co.code.add(new Addi("sp", Integer.toString(totalFrameSize), "sp"));

		int intCount = 0;
		int floatCount = 0;

		List<String> intRegs = new ArrayList<>();
		List<String> floatRegs = new ArrayList<>();

		List<String> usedRegs = ra.getUsedRegs();
		for (String reg : usedRegs) {
			if (reg.startsWith("x")) {
				intRegs.add(reg);
				intCount++;
			}
			else {
				floatRegs.add(reg);
				floatCount++;
			}
		}

		// Save integer registers
		for (int i = 0; i < intCount; i++) {
			co.code.add(new Sw(intRegs.get(i), "sp", "0"));
			co.code.add(new Addi("sp", "-4", "sp"));
		}
		// Save floating-point registers
		for (int i = 0; i < floatCount; i++) {
			co.code.add(new Fsw(floatRegs.get(i), "sp", "0"));  // Assuming 4 bytes per float register
			co.code.add(new Addi("sp", "-4", "sp"));
		}

		co.code.addAll(body.getCode());

		String funcOutLabel = generateFunctionOutLabel();
		co.code.add(new Label(funcOutLabel));

		// Restore floating-point registers
		for (int i = floatCount - 1; i >= 0; i--) {
			co.code.add(new Addi("sp", "4", "sp"));	
			co.code.add(new Flw(floatRegs.get(i), "sp", "0"));
		}

		// Restore integer registers
		for (int i = intCount - 1; i >= 0; i--) {
			co.code.add(new Addi("sp", "4", "sp"));
			co.code.add(new Lw(intRegs.get(i), "sp", "0"));
		}
		
		co.code.add(new Mv("fp", "sp"));
		co.code.add(new Lw("fp", "fp", "0"));
		co.code.add(new Ret());

		co.lval = false;
		co.type = body.getType();
		/** ADD REGISTER ALLOCATION HERE
		 * 
		 * You may find it useful to do this in the following way:
		 * 
		 * 1. Write a register allocator class that is initialized with the number of int/fp registers to use, the code from
		 * 		`body`, and the function scope from `node` (the function scope gives you access to local/global variables)
		 * 2. Within the register allocator class, do the following
		 * 		a. Split the code in body into basic blocks
		 * 		b. (573 version) Perform liveness analysis on each basic block (assume globals and locals are live)
		 * 		b. (468/595 version) Assume all locals/globals/temporaries are live all the time
		 * 		c. Perform register allocation on each basic block using the algorithms presented in class,
		 * 			converting 3AC into assembly code with macro expansion
		 * 			i. Add code to track the state of the registers for each basic block (what is assigned to the register, whether it's dirty)
		 * 			ii. As you perform register allocation within a basic block, spill registers to memory as necessary. Use any
		 * 				heuristic you want to determine which registers to allocate and which to spill
		 * 			iii. If you need to spill a temporary to memory, you'll find it easiest to add the temporary as a new "local" variable
		 * 				to the local scope (you can just use the temporary name as the variable name); that will automatically allocate a spot
		 * 				in the activation record for it.
		 * 			iv. At the end of each basic block, save all dirty/live registers that hold globals/locals back to the stack
		 * 3. Once register allocation is done, track:
		 * 		a. How big the local scope is after spilling temporaries -- this affects allocating the stack frame
		 * 		b. How many total registers you used -- this affects the register save/restore code
		 * 4. Now generate code for your function as before, but using the updated information for register save/restore and frame allocation
		 */


		return co;
	}

	/**
	 * Generate code for the list of functions. This is the "top level" code generation function
	 * 
	 * Step 1: Set fp to point to sp
	 * 
	 * Step 2: Insert a JR to main
	 * 
	 * Step 3: Insert a HALT
	 * 
	 * Step 4: Include all the code of the functions
	 */
	@Override
	protected CodeObject postprocess(FunctionListNode node, List<CodeObject> funcs) {
		CodeObject co = new CodeObject();

		co.code.add(new Mv("sp", "fp"));
		co.code.add(new Jr(generateFunctionLabel("main")));
		co.code.add(new Halt());
		co.code.add(new Blank());

		//add code for each of the functions
		for (CodeObject c : funcs) {
			co.code.addAll(c.code);
			co.code.add(new Blank());
		}

		return co;
	}

	/**
	* 
	* FILL IN FOR STEP 4
	* 
	* Generate code for a call expression
	 * 
	 * Step 1: For each argument:
	 * 
	 * 	Step 1a: insert code of argument (don't forget to rvalify!)
	 * 
	 * 	Step 1b: push result of argument onto stack 
	 * 
	 * Step 2: allocate space for return value
	 * 
	 * Step 3: push current return address onto stack
	 * 
	 * Step 4: jump to function
	 * 
	 * Step 5: pop return address back from stack
	 * 
	 * Step 6: pop return value into fresh temporary (destination of call expression)
	 * 
	 * Step 7: remove arguments from stack (move sp)
	 */
	@Override
	protected CodeObject postprocess(CallNode node, List<CodeObject> args) {
		
		//STEP 0
		CodeObject co = new CodeObject();

		/* FILL IN FROM STEP 4*/
		// Add each arg into the stack
		for (CodeObject c: args){

			co.code.addAll(c.getCode());

			if (c.getType() == Scope.Type.FLOAT){
				co.code.add(new Fsw(c.temp, "sp", "0"));
			}
			else{
				co.code.add(new Sw(c.temp, "sp", "0"));
			}
			co.code.add(new Addi("sp", "-4", "sp"));
		}

		// Save space for Return Value
		co.code.add(new Addi("sp", "-4", "sp"));
		
		// Push return address onto stack
		co.code.add(new Sw("ra", "sp", "0"));
		co.code.add(new Addi("sp", "-4", "sp"));
		
		co.code.add(new Jr(generateFunctionLabel(node.getFuncName())));
		co.code.add(new Addi("sp", "4", "sp"));
		
		// Pop return address off of stack
		co.code.add(new Lw("ra", "sp", "0"));
		co.code.add(new Addi("sp", "4", "sp"));
		String newTemp = generateTemp(node.getType());
		if (node.getType() == Scope.Type.FLOAT) {
			co.code.add(new Flw(newTemp, "sp", "0"));
		}
		else {
			co.code.add(new Lw(newTemp, "sp", "0"));
		}
		co.code.add(new Addi("sp", String.valueOf(args.size() * 4), "sp"));

		co.lval = false;
		co.temp = newTemp;
		co.type = node.getType();
		/* MODIFY THIS TO GENERATE 3AC */

		return co;
	}	
	
	/**
	 * Generate a fresh temporary
	 * 
	 * @return new temporary register name
	 */
	protected String generateTemp(Scope.Type t) {
		switch(t) {
			case INT: return intTempPrefix + String.valueOf(++intRegCount);
			case FLOAT: return floatTempPrefix + String.valueOf(++floatRegCount);
			default: throw new Error("Generating temp for bad type");
		}
	}

	protected String generateLoopLabel() {
		return "loop_" + String.valueOf(++loopLabel);
	}

	protected String generateElseLabel() {
		return  "else_" + String.valueOf(++elseLabel);
	}

	protected String generateOutLabel() {
		return "out_" +  String.valueOf(++outLabel);
	}

	protected String generateFunctionLabel() {
		return "func_" + currFunc;
	}

	protected String generateFunctionLabel(String func) {
		return "func_" + func;
	}

	protected String generateFunctionOutLabel() {
		return "func_ret_" + currFunc;
	}
	
	/**
	 * Take a code object that results in an lval, and create a new code
	 * object that adds a load to generate the rval.
	 * 
	 * @param lco The code object resulting in an address
	 * @return A code object with all the code of <code>lco</code> followed by a load
	 *         to generate an rval
	 */
	protected CodeObject rvalify(CodeObject lco) {
		
		assert (lco.lval == true);
		CodeObject co = new CodeObject();

		/* THIS WON'T BE NECESSARY IF YOU'RE GENERATING 3AC */

		/* DON'T FORGET TO ADD CODE TO GENERATE LOADS FOR LOCAL VARIABLES */
		String offset = "0";
		String newTemp = "";
		if (lco.isVar()) {
			if (lco.getSTE().isLocal()) {
				lco.temp = "fp";
				offset = lco.getSTE().addressToString();
				lco.lval = false;
				lco.type = lco.getSTE().getType();
			}
			else {
				InstructionList il = generateAddrFromVariable(lco);
				co.code.addAll(il);
				lco.temp = il.getLast().getDest();
				lco.type = lco.getSTE().getType();
				lco.lval = false;
			}
			newTemp = generateTemp(lco.type);
			switch(lco.getType()) {
				case INT: 
					co.code.add(new Lw(newTemp, lco.temp, offset));
					break;
				case FLOAT:
					co.code.add(new Flw(newTemp, lco.temp, offset));
					break;
				default:
					throw new Error("No other types allowed.");
			}
		}

		co.temp = newTemp;
		co.lval = false;
		co.type = lco.getType();

		return co;
	}

	/**
	 * Generate an instruction sequence that holds the address of the variable in a code object
	 * 
	 * If it's a global variable, just get the address from the symbol table
	 * 
	 * If it's a local variable, compute the address relative to the frame pointer (fp)
	 * 
	 * @param lco The code object holding a variable
	 * @return a list of instructions that puts the address of the variable in a register
	 */
	private InstructionList generateAddrFromVariable(CodeObject lco) {

		InstructionList il = new InstructionList();

		//Step 1:
		SymbolTableEntry symbol = lco.getSTE();
		String address = symbol.addressToString();

		//Step 2:
		Instruction compAddr = null;
		if (symbol.isLocal()) {
			//If local, address is offset
			//need to load fp + offset
			//addi tmp' fp offset
			compAddr = new Addi("fp", address, generateTemp(Scope.Type.INT));
		} else {
			//If global, address in symbol table is the right location
			//la tmp' addr //Register type needs to be an int
			compAddr = new La(generateTemp(Scope.Type.INT), address);
		}
		il.add(compAddr); //add instruction to code object

		return il;
	}

}
