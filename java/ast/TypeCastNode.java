package ast;

import ast.visitor.ASTVisitor;
import compiler.Scope;

/**
 * Type Cast node
 */
public class TypeCastNode extends ExpressionNode {

	private ExpressionNode expr;
	
	public TypeCastNode(ExpressionNode expr, Scope.Type type) {
        this.expr = expr;
        this.setType(type);
	}

	@Override
	public <R> R accept(ASTVisitor<R> visitor) {
		return visitor.visit(this);
	}

	public ExpressionNode getExpr() {
		return this.expr;
	}

    public Scope.Type getType(){
        return type;
    }

}
