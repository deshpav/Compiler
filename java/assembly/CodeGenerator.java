package assembly;

import java.util.List;

import compiler.Scope.InnerType;
import compiler.Scope.SymbolTableEntry;
import ast.visitor.AbstractASTVisitor;

import ast.*;
import assembly.instructions.*;
import compiler.Scope;

public class CodeGenerator extends AbstractASTVisitor<CodeObject> {

	int intRegCount;
	int floatRegCount;
	static final public char intTempPrefix = 't';
	static final public char floatTempPrefix = 'f';
	
	int loopLabel;
	int elseLabel;
	int outLabel;

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
	 * Important: add a pointer from the code object to the symbol table entry
	 *            so we know how to generate code for it later (we'll need to find
	 *            the address)
	 * 
	 * Mark the code object as holding a variable, and also as an lval
	 */
	@Override
	protected CodeObject postprocess(VarNode node) {
		
		Scope.SymbolTableEntry sym = node.getSymbol();
		
		CodeObject co = new CodeObject(sym);
		co.lval = true;
		co.type = node.getType();

		return co;
	}

	/** Generate code for IntLiterals
	 * 
	 * Use load immediate instruction to do this.
	 */
	@Override
	protected CodeObject postprocess(IntLitNode node) {
		CodeObject co = new CodeObject();
		
		//Load an immediate into a register
		//The li and la instructions are the same, but it's helpful to distinguish
		//for readability purposes.
		//li tmp' value
		Instruction i = new Li(generateTemp(Scope.InnerType.INT), node.getVal());

		co.code.add(i); //add this instruction to the code object
		co.lval = false; //co holds an rval -- data
		co.temp = i.getDest(); //temp is in destination of li
		co.type = node.getType();

		return co;
	}

	/** Generate code for FloatLiteras
	 * 
	 * Use load immediate instruction to do this.
	 */
	@Override
	protected CodeObject postprocess(FloatLitNode node) {
		CodeObject co = new CodeObject();
		
		//Load an immediate into a regisster
		//The li and la instructions are the same, but it's helpful to distinguish
		//for readability purposes.
		//li tmp' value
		Instruction i = new FImm(generateTemp(Scope.InnerType.FLOAT), node.getVal());

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
		if (left.lval) {
			left = rvalify(left);
		}
		co.code.addAll(left.code);
		//System.err.println("BINrvalify" + co.code.getLast());
		
		if (right.lval) {
			right = rvalify(right);
		}
		co.code.addAll(right.code);
		//System.err.println("BINrvalify" + co.code.getLast());

		Scope.InnerType currType = node.getType().type;
		String newTemp;
		if (left.getType().type == Scope.InnerType.FLOAT && right.getType().type == Scope.InnerType.INT){
			newTemp = generateTemp(Scope.InnerType.FLOAT);
			co.code.add(new IMovF(right.temp, newTemp));
			right.temp = newTemp;
			currType = Scope.InnerType.FLOAT;
		}
		else if (left.getType().type == Scope.InnerType.INT && right.getType().type == Scope.InnerType.FLOAT){
			newTemp = generateTemp(Scope.InnerType.FLOAT);
			co.code.add(new IMovF(left.temp, newTemp));
			left.temp = newTemp;
			currType = Scope.InnerType.FLOAT;
		}

		
		Instruction binaryOp = null;
		if (currType == InnerType.INT || currType == InnerType.PTR) {
			switch (node.getOp()) {
				case ADD:
					binaryOp = new Add(left.temp, right.temp, generateTemp(Scope.InnerType.INT));
					break;
				case SUB:
					binaryOp = new Sub(left.temp, right.temp, generateTemp(Scope.InnerType.INT));
					break;
				case MUL:
					binaryOp = new Mul(left.temp, right.temp, generateTemp(Scope.InnerType.INT));
					break;
				case DIV:
					binaryOp = new Div(left.temp, right.temp, generateTemp(Scope.InnerType.INT));
					break;
				default:
					binaryOp = null;
					break;
			}
			co.type = new Scope.Type(Scope.InnerType.INT);
		}
		else if (currType == InnerType.FLOAT){
			switch (node.getOp()) {
				case ADD:
					binaryOp = new FAdd(left.temp, right.temp, generateTemp(Scope.InnerType.FLOAT));
					break;
				case SUB:
					binaryOp = new FSub(left.temp, right.temp, generateTemp(Scope.InnerType.FLOAT));
					break;
				case MUL:
					binaryOp = new FMul(left.temp, right.temp, generateTemp(Scope.InnerType.FLOAT));
					break;
				case DIV:
					binaryOp = new FDiv(left.temp, right.temp, generateTemp(Scope.InnerType.FLOAT));
					break;
				default:
					binaryOp = null;
					break;
			}
			co.type = new Scope.Type(Scope.InnerType.FLOAT);
		}

    	co.code.add(binaryOp);

		co.temp = binaryOp.getDest();
    	co.lval = false;

		return co;
	}

	/**
	 * Generate code for unary operations.
	 * 
	 * Step 0: create new code object
	 * Step 1: add code from child expression
	 * Step 1a: if child is an lval, add a load to get the data
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

		//co.code.addAll(expr.code);
		if (expr.lval) {
			expr = rvalify(expr);
		}
		co.code.addAll(expr.code);
		//System.err.println("UNrvalify" + co.code.getLast());

		Instruction unaryOp = null;
		if (node.getOp() == UnaryOpNode.OpType.NEG){
			switch (node.getType().type){
				case PTR:
				case INT:
					unaryOp = new Neg(expr.temp, generateTemp(Scope.InnerType.INT));
					break;
				case FLOAT:
					unaryOp = new FNeg(expr.temp, generateTemp(Scope.InnerType.FLOAT));
					break;
				default:
					break;
			}
		}
		co.code.add(unaryOp);

		// Update the temp and lval fields
		co.temp = unaryOp.getDest();
		co.lval = false;
		co.type = node.getType();

		return co;
	}

	@Override
	protected CodeObject postprocess(TypeCastNode node, CodeObject expr) {
		CodeObject co = new CodeObject();

		if (expr.lval){
			expr = rvalify(expr);
		}
		co.code.addAll(expr.code);

		String newTemp = "";
		switch (node.getType().type){
			case FLOAT:
				if (expr.getType().type == Scope.InnerType.INT){
					newTemp = generateTemp(Scope.InnerType.FLOAT);
					co.code.add(new IMovF(expr.temp, newTemp));
					co.type = new Scope.Type(Scope.InnerType.FLOAT);
				}
				break;
			case INT:
				if (expr.getType().type == Scope.InnerType.FLOAT){
					newTemp = generateTemp(Scope.InnerType.INT);
					co.code.add(new FMovI(expr.temp, newTemp));
					co.type = new Scope.Type(Scope.InnerType.INT);
				}
				break;
			default:
				break;
		}

		co.temp = newTemp;
		co.lval = false;
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
		assert(left.lval == true); //left hand side had better hold an address
		
		//InstructionList addrVar = new InstructionList();
		//Step 1a
		String offset = "0";
		
		if ((left.isVar() && !left.getSTE().isLocal())){
			InstructionList instrL = generateAddrFromVariable(left);
			co.code.addAll(instrL);
			left.temp = instrL.getLast().getDest();
		}
		co.code.addAll(left.code);

		if (right.lval) {
			right = rvalify(right);
		}		
		co.code.addAll(right.code);
		//System.err.println("ASrvalify" + co.code.getLast());

		String newTemp;
		if (left.getType().type == Scope.InnerType.FLOAT && right.getType().type == Scope.InnerType.INT){
			newTemp = generateTemp(Scope.InnerType.FLOAT);
			co.code.add(new IMovF(right.temp, newTemp));
			right.temp = newTemp;
		}
		else if (left.getType().type == Scope.InnerType.INT && right.getType().type == Scope.InnerType.FLOAT){
			newTemp = generateTemp(Scope.InnerType.INT);
			co.code.add(new FMovI(right.temp, newTemp));
			right.temp = newTemp;
		}

		if (left.getSTE() != null) {
		  if (left.getSTE().isLocal()) {
			offset = left.getSTE().addressToString();
			switch(left.getType().type) {
			  case PTR:
			  case INT:
				co.code.add(new Sw(right.temp, "fp", offset));
				co.type = new Scope.Type(Scope.InnerType.INT);
				break;
			  case FLOAT:
				co.code.add(new Fsw(right.temp, "fp", offset));
				co.type = new Scope.Type(Scope.InnerType.FLOAT);
				break;
			  default:
				break;
			}
		  }
		  else {

			switch (left.getType().type) {
			  case PTR:
			  case INT:
				co.code.add(new Sw(right.temp, left.temp, "0"));
				co.type = new Scope.Type(Scope.InnerType.INT);
				break;
			  case FLOAT:
				co.code.add(new Fsw(right.temp, left.temp, "0"));
				co.type = new Scope.Type(Scope.InnerType.FLOAT);
				break;
			  default:
				break;
			}
		  }
		}
		else {
		  switch(left.getType().type) {
			case PTR:
			case INT:
			  co.code.add(new Sw(right.temp, left.temp, "0"));
			  break;
			case FLOAT:
			  co.code.add(new Fsw(right.temp, left.temp, "0"));
			  break;
			default:
				break;
		  }
		}

		co.lval = false;
		
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
	 */
	@Override
	protected CodeObject postprocess(ReadNode node, CodeObject var) {
		
		//Step 0
		CodeObject co = new CodeObject();

		//Generating code for read(id)
		assert(var.getSTE() != null); //var had better be a variable

		InstructionList il = new InstructionList();
		switch(node.getType().type) {
			case INT: 
				//Code to generate if INT:
				//geti tmp
				//if var is global: la tmp', <var>; sw tmp 0(tmp')
				//if var is local: sw tmp offset(fp)
				Instruction geti = new GetI(generateTemp(Scope.InnerType.INT));
				il.add(geti);
				InstructionList store = new InstructionList();
				if (var.getSTE().isLocal()) {
					store.add(new Sw(geti.getDest(), "fp", String.valueOf(var.getSTE().addressToString())));
				} else {
					store.addAll(generateAddrFromVariable(var));
					store.add(new Sw(geti.getDest(), store.getLast().getDest(), "0"));
				}
				il.addAll(store);
				break;
			case FLOAT:
				//Code to generate if FLOAT:
				//getf tmp
				//if var is global: la tmp', <var>; fsw tmp 0(tmp')
				//if var is local: fsw tmp offset(fp)
				Instruction getf = new GetF(generateTemp(Scope.InnerType.FLOAT));
				il.add(getf);
				InstructionList fstore = new InstructionList();
				if (var.getSTE().isLocal()) {
					fstore.add(new Fsw(getf.getDest(), "fp", String.valueOf(var.getSTE().addressToString())));
				} else {
					fstore.addAll(generateAddrFromVariable(var));
					fstore.add(new Fsw(getf.getDest(), fstore.getLast().getDest(), "0"));
				}
				il.addAll(fstore);
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
	 */
	@Override
	protected CodeObject postprocess(WriteNode node, CodeObject expr) {
		CodeObject co = new CodeObject();

		//generating code for write(expr)

		//for strings, we expect a variable
		if (node.getWriteExpr().getType().type == Scope.InnerType.STRING) {
			//Step 1:
			assert(expr.getSTE() != null);
			
			System.out.println("; generating code to print " + expr.getSTE());

			//Get the address of the variable
			InstructionList addrCo = generateAddrFromVariable(expr);
			co.code.addAll(addrCo);

			//Step 2:
			Instruction write = new PutS(addrCo.getLast().getDest());
			co.code.add(write);
		} else {
			//Step 1a:
			//if expr is an lval, load from it
			if (expr.lval == true) {
				expr = rvalify(expr);
			}
			
			//Step 1:
			co.code.addAll(expr.code);

			//Step 2:
			//if type of writenode is int, use puti, if float, use putf
			Instruction write = null;
			switch(node.getWriteExpr().getType().type) {
			case STRING: throw new Error("Shouldn't have a STRING here");
			case INT: 
			case PTR: //should work the same way for pointers
				write = new PutI(expr.temp); break;
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
		if (left.lval) {
			left = rvalify(left);
		}
		co.code.addAll(left.getCode());
		//System.err.println("CONDrvalify" + co.code.getLast());
		
		if (right.lval) {
			right = rvalify(right);
		}
		co.code.addAll(right.getCode());
		//System.err.println("CONDrvalify" + co.code.getLast());

		String newTemp;
		if (left.getType().type == Scope.InnerType.FLOAT && right.getType().type == Scope.InnerType.INT){
			newTemp = generateTemp(Scope.InnerType.FLOAT);
			co.code.add(new IMovF(right.temp, newTemp));
			right.temp = newTemp;
			co.type = new Scope.Type(Scope.InnerType.FLOAT);
		}
		else if (left.getType().type == Scope.InnerType.INT && right.getType().type == Scope.InnerType.FLOAT){
			newTemp = generateTemp(Scope.InnerType.FLOAT);
			co.code.add(new IMovF(left.temp, newTemp));
			left.temp = newTemp;
			co.type = new Scope.Type(Scope.InnerType.FLOAT);
		}
		else{
			co.type = left.getType();
		}
		
		co.temp = left.temp + " " + right.temp;
		co.lval = false;
		

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
		String flt_dest = generateTemp(Scope.InnerType.INT);
		if (cond.getType().type == Scope.InnerType.INT){
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
		} else if (cond.getType().type == Scope.InnerType.FLOAT) {
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

		co.code.addAll(cond.getCode());
		String[] parts = cond.temp.split(" ");
		String l_node = parts[0];
		String r_node = parts[1];

		Instruction branch = null;
		Instruction branch2 = null;
		String flt_dest = generateTemp(Scope.InnerType.INT);
		if (cond.getType().type == Scope.InnerType.INT || cond.getType().type == Scope.InnerType.PTR){
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
		} else if (cond.getType().type == Scope.InnerType.FLOAT) {
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

		/* FILL IN FROM STEP 4 */
		
		if (retExpr != null){
			if (retExpr.lval) {
			retExpr = rvalify(retExpr);
			}

			co.code.addAll(retExpr.getCode());
			//System.err.println("RETrvalify" + co.code.getLast());
			
			switch(retExpr.getType().type){
				case PTR:
				case VOID:
				case INFER:
				case INT:
					co.code.add(new Sw(retExpr.temp, "fp", "8"));
					break;
				case FLOAT:
					co.code.add(new Fsw(retExpr.temp, "fp", "8"));
					break;
				default:
					break;
			}

			co.code.add(new J(generateFunctionOutLabel()));
			co.lval = false;
		}

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

		/* FILL IN */
		co.code.add(new Label(generateFunctionLabel(node.getFuncName())));
		co.code.add(new Sw("fp", "sp", "0"));
		co.code.add(new Mv("sp", "fp"));
		co.code.add(new Addi("sp", "-4", "sp"));

		int totalFrameSize = node.getScope().getNumLocals() * -4;  // Assuming 4 bytes for each local variable
		co.code.add(new Addi("sp", Integer.toString(totalFrameSize), "sp"));

		int intCount = getIntRegCount();
		int floatCount = getFloatRegCount();

		// Save integer registers
		for (int i = 1; i <= intCount; i++) {
			co.code.add(new Sw("t" + Integer.toString(i), "sp", "0"));
			co.code.add(new Addi("sp", "-4", "sp"));
		}
		// Save floating-point registers
		for (int i = 1; i <= floatCount; i++) {
			co.code.add(new Fsw("f" + i, "sp", "0"));  // Assuming 4 bytes per float register
			co.code.add(new Addi("sp", "-4", "sp"));
		}

		co.code.addAll(body.getCode());

		String funcOutLabel = generateFunctionOutLabel();
		co.code.add(new Label(funcOutLabel));

		// Restore floating-point registers
		for (int i = floatCount; i > 0; i--) {
			co.code.add(new Addi("sp", "4", "sp"));	
			co.code.add(new Flw("f" + Integer.toString(i), "sp", "0"));
		}

		// Restore integer registers
		for (int i = intCount; i > 0; i--) {
			co.code.add(new Addi("sp", "4", "sp"));
			co.code.add(new Lw("t" + Integer.toString(i), "sp", "0"));
		}
		
		co.code.add(new Mv("fp", "sp"));
		co.code.add(new Lw("fp", "fp", "0"));
		co.code.add(new Ret());

		co.lval = false;
		co.type = body.getType();

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
	 * Step 2: alloate space for return value
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
	 * 
	 * Add special handling for malloc and free
	 */

	 /**
	  * FOR STEP 6: Make sure to handle VOID functions properly
	  */
	@Override
	protected CodeObject postprocess(CallNode node, List<CodeObject> args) {
		
		//STEP 0
		CodeObject co = new CodeObject();

		/* FILL IN FROM STEP 4 */
		
		// Add each arg into the stack
		for (CodeObject c: args){
		
			if (c.lval){
				c = rvalify(c);
			}
			
			co.code.addAll(c.getCode());
			//System.err.println("CALLrvalify" + co.code.getLast());

			switch (c.getType().type){
				case PTR:
				// case VOID:
				// case INFER:
				case INT:
					co.code.add(new Sw(c.temp, "sp", "0"));
					break;
				case FLOAT:
					co.code.add(new Fsw(c.temp, "sp", "0"));
					break;
				default:
					break;
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


		String newTemp = "";
		switch(node.getType().type){
			case PTR:
			case INT:
				newTemp = generateTemp(Scope.InnerType.INT);
				co.code.add(new Lw(newTemp, "sp", "0"));
				break;
			case FLOAT:
				newTemp = generateTemp(Scope.InnerType.FLOAT);
				co.code.add(new Flw(newTemp, "sp", "0"));
				break;
			default:
				break;
		}

		co.code.add(new Addi("sp", String.valueOf(args.size() * 4), "sp"));

		co.lval = false;
		co.temp = newTemp;
		co.type = node.getType();

		return co;
	}	
	
	/**
	 * Generate code for * (expr)
	 * 
	 * Goal: convert the r-val coming from expr (a computed address) into an l-val (an address that can be loaded/stored)
	 * 
	 * Step 0: Create new code object
	 * 
	 * Step 1: Rvalify expr if needed
	 * 
	 * Step 2: Copy code from expr (including any rvalification) into new code object
	 * 
	 * Step 3: New code object has same temporary as old code, but now is marked as an l-val
	 * 
	 * Step 4: New code object has an "unwrapped" type: if type of expr is * T, type of temporary is T. Can get this from node
	 */
	@Override
	protected CodeObject postprocess(PtrDerefNode node, CodeObject expr) {
		CodeObject co = new CodeObject();

		/* FILL IN FOR STEP 6 */

		if (expr.lval){
			expr = rvalify(expr);
		}
		co.code.addAll(expr.code);
		//System.err.println("PTRrvalify" + co.code.getLast());
		
		co.temp = expr.temp;
		co.lval = true;
		co.type = node.getType();

		return co;
	}

	/**
	 * Generate code for a & (expr)
	 * 
	 * Goal: convert the lval coming from expr (an address) to an r-val (a piece of data that can be used)
	 * 
	 * Step 0: Create new code object
	 * 
	 * Step 1: If lval is a variable, generate code to put address into a register (e.g., generateAddressFromVar)
	 *			Otherwise just copy code from other code object
	 * 
	 * Step 2: New code object has same temporary as existing code, but is an r-val
	 * 
	 * Step 3: New code object has a "wrapped" type. If type of expr is T, type of temporary is *T. Can get this from node
	 */
	@Override
	protected CodeObject postprocess(AddrOfNode node, CodeObject expr) {
		CodeObject co = new CodeObject();

		if (expr.isVar()){
			InstructionList addrVar = generateAddrFromVariable(expr);
			co.code.addAll(addrVar);
			co.temp = addrVar.getLast().getDest();
		}
		else{
			co.code.addAll(expr.code);
			co.temp = expr.temp;
		}

		// new code already has same temporary as old code from if-else condition, setting lval false since it's an rval
		co.lval = false;
		co.type = node.getType();

		return co;
	}

	/**
	 * Generate code for malloc
	 * 
	 * Step 0: Create new code object
	 * 
	 * Step 1: Add code from expression (rvalify if needed)
	 * 
	 * Step 2: Create new MALLOC instruction
	 * 
	 * Step 3: Set code object type to INFER
	 */
	@Override
	protected CodeObject postprocess(MallocNode node, CodeObject expr) {
		CodeObject co = new CodeObject();

		/* FILL IN FOR STEP 6 */
		if (expr.lval){
			expr = rvalify(expr);
		}
		co.code.addAll(expr.code);
		//System.err.println("MLCrvalify" + co.code.getLast());

		String newTemp = generateTemp(Scope.InnerType.INT);
		Instruction memoryAlloc = new Malloc(expr.temp, newTemp);
		co.code.add(memoryAlloc);

		co.temp = newTemp;
		co.type = new Scope.Type(Scope.InnerType.INFER);
		
		return co;
	}
	
	/**
	 * Generate code for free
	 * 
	 * Step 0: Create new code object
	 * 
	 * Step 1: Add code from expression (rvalify if needed)
	 * 
	 * Step 2: Create new FREE instruction
	 */
	@Override
	protected CodeObject postprocess(FreeNode node, CodeObject expr) {
		CodeObject co = new CodeObject();

		/* FILL IN FOR STEP 6 */
		if (expr.lval){
			expr = rvalify(expr);
		}
		co.code.addAll(expr.code);

		Instruction freeInstr = new Free(expr.temp);
		co.code.add(freeInstr);

		return co;
	}

	/**
	 * Generate a fresh temporary
	 * 
	 * @return new temporary register name
	 */
	protected String generateTemp(Scope.InnerType t) {
		switch(t) {
			case INT: 
			case PTR: //works the same for pointers
				return intTempPrefix + String.valueOf(++intRegCount);
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

		/* FILL IN FROM STEP 2 */

		co.code.addAll(lco.code);
		String offset="";
		String newTemp = "";
		if (lco.getSTE() != null) {
		  if (lco.getSTE().isLocal()) {
			offset = lco.getSTE().addressToString();
			switch(lco.getType().type) {
			  case PTR:
			  case INT:
				newTemp = generateTemp(Scope.InnerType.INT);
				co.code.add(new Lw(newTemp, "fp", offset));
				break;
			  case FLOAT:
				newTemp = generateTemp(Scope.InnerType.FLOAT);
				co.code.add(new Flw(newTemp, "fp", offset));
				break;
			  default:
				break;
			}
		  }
		  else {
			InstructionList il = generateAddrFromVariable(lco);
			co.code.addAll(il);
			lco.temp = il.getLast().getDest();

			switch (lco.getType().type) {
			  case PTR:
			  case INT:
				newTemp = generateTemp(Scope.InnerType.INT);
				co.code.add(new Lw(newTemp, lco.temp, "0"));
				break;
			  case FLOAT:
				newTemp = generateTemp(Scope.InnerType.FLOAT);
				co.code.add(new Flw(newTemp, lco.temp, "0"));
				break;
			  default:
				break;
			}
		  }
		}
		else {
		  switch(lco.getType().type) {
			case PTR:
			case INT:
			  newTemp = generateTemp(Scope.InnerType.INT);
			  co.code.add(new Lw(newTemp, lco.temp, "0"));
			  break;
			case FLOAT:
			  newTemp = generateTemp(Scope.InnerType.FLOAT);
			  co.code.add(new Flw(newTemp, lco.temp, "0"));
			  break;
			default:
				break;
		  }
		}

		//System.err.println("--rvalify-- " + newTemp + co.code.getLast());
		co.temp = newTemp;
		co.type = lco.getType();
		co.lval = false;

		/* DON'T FORGET TO ADD CODE TO GENERATE LOADS FOR LOCAL VARIABLES */

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
			compAddr = new Addi("fp", address, generateTemp(Scope.InnerType.INT));
		} else {
			//If global, address in symbol table is the right location
			//la tmp' addr //Register type needs to be an int
			compAddr = new La(generateTemp(Scope.InnerType.INT), address);
		}
		il.add(compAddr); //add instruction to code object

		return il;
	}

}
