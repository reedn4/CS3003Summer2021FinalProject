// Sourced from https://github.com/NickStephens/Clite
package clite;

import java.util.*;

public class TypeTransformer {

    public static Program T (Program p, TypeMap tm) {
	Functions t_funcs = new Functions();
    	for (int i=0; i<p.functions.size(); i++) {
		Function func = p.functions.get(i);
        	Function t_func = T(func, StaticTypeCheck.typing(p.globals, p.functions, func));
		t_funcs.add(t_func);
	}
        return new Program(p.globals, t_funcs);
    } 

    public static Function T (Function f, TypeMap tm) {
    	Block t_body = (Block) T(f.body, tm);
    	return new Function(f.t, f.id, f.params, f.locals, t_body); 
    }

    public static Expression T (Expression e, TypeMap tm) {
        if (e instanceof Value) 
            return e;
	if (e instanceof ArrayRef) {
	    ArrayRef a = (ArrayRef) e;
	    return new ArrayRef(a.id, T(a.index, tm));
	}	
        if (e instanceof VariableRef) 
            return e;
        if (e instanceof Binary) {
            Binary b = (Binary)e; 
            Type typ1 = StaticTypeCheck.typeOf(b.term1, tm);
            Type typ2 = StaticTypeCheck.typeOf(b.term2, tm);
            Expression t1 = T (b.term1, tm);
            Expression t2 = T (b.term2, tm);
            if (typ1 == Type.INT) {
            	if (typ2 == Type.FLOAT)
            		t1 = new Unary (new Operator(Operator.I2F), t1);
            	else if (typ2 == Type.DOUBLE)
            		t1 = new Unary (new Operator(Operator.I2D), t1);
            	return new Binary(b.op.intMap(b.op.val), t1,t2);
            } else if (typ1 == Type.FLOAT) { 
            	if (typ2 == Type.INT)	
            		t2 = new Unary (new Operator(Operator.I2F), t2);
            	else if (typ2 == Type.DOUBLE)	
            		t2 = new Unary (new Operator(Operator.D2F), t2);
            	return new Binary(b.op.floatMap(b.op.val), t1,t2);
            } else if (typ1 == Type.DOUBLE) { 
            	if (typ2 == Type.INT)	
            		t2 = new Unary (new Operator(Operator.I2D), t2);
            	else if (typ2 == Type.FLOAT)
            		t2 = new Unary (new Operator(Operator.F2D), t2);
            	return new Binary(b.op.doubleMap(b.op.val), t1,t2);
    	    } else if (typ1 == Type.CHAR) 
                return new Binary(b.op.charMap(b.op.val), t1,t2);
            else if (typ1 == Type.BOOL) 
                return new Binary(b.op.boolMap(b.op.val), t1,t2);
            throw new IllegalArgumentException("should never reach here");
        }
        // student exercise
	if (e instanceof Unary) {
	    Unary u = (Unary)e;
	    Type typ = StaticTypeCheck.typeOf(u.term, tm);
	    Expression t = T (u.term, tm);
	    if (typ == Type.INT)
	    	return new Unary(u.op.intMap(u.op.val), t);
	    else if (typ == Type.FLOAT)
	    	return new Unary(u.op.floatMap(u.op.val), t);
	    else if (typ == Type.DOUBLE)
			return new Unary(u.op.doubleMap(u.op.val), t);
	    else if (typ == Type.CHAR)
	    	return new Unary(u.op.charMap(u.op.val), t);
	    else if (typ == Type.BOOL)
	    	return new Unary(u.op.boolMap(u.op.val), t);
	    throw new IllegalArgumentException("should never reach here");
	}
	if (e instanceof CallExpression) {
		CallExpression c = (CallExpression) e;
		//Looking for typemap associated with call's name
		Object o = tm.get(new Variable(c.name));
		FunctionMap fm = (FunctionMap) o; 
		FunctionTypeMap called_params = (FunctionTypeMap) fm.getParams();	

		ArrayList<Type> param_types = called_params.typeArray(); 

		for (int i=0; i<c.args.size(); i++) {
			Type current_arg_type = StaticTypeCheck.typeOf(c.args.get(i), tm);
			if (param_types.get(i).equals(Type.FLOAT)) {
				if (current_arg_type.equals(Type.INT))
					c.args.set(i, new Unary(new Operator(Operator.I2F), T(c.args.get(i), tm)));	
				else
					c.args.set(i, T(c.args.get(i), tm));
			}
			else {
				c.args.set(i, T(c.args.get(i), tm));
			}
		} 
		return new CallExpression(c.name, c.args);

	}
	throw new IllegalArgumentException("should never reach here");
    }

    public static Statement T (Statement s, TypeMap tm) {
        if (s instanceof Skip) return s;
        if (s instanceof Assignment) {
            Assignment a = (Assignment)s;
	    VariableRef target;
	    if (a.target instanceof ArrayRef) {
 	           target = (ArrayRef) T(a.target, tm);
	    }
	    else {
	    	   target = (Variable) a.target;
	    }
            Expression src = T (a.source, tm);
            Type ttype = (Type)tm.get(target);
            Type srctype = StaticTypeCheck.typeOf(a.source, tm);
            if (ttype == Type.FLOAT) {
                if (srctype == Type.INT) {
                    src = new Unary(new Operator(Operator.I2F), src);
                    srctype = Type.FLOAT;
                }
            }
            else if (ttype == Type.INT) {
                if (srctype == Type.CHAR) {
                    src = new Unary(new Operator(Operator.C2I), src);
                    srctype = Type.INT;
                }
            }
            StaticTypeCheck.check( ttype == srctype,
                      "bug in assignment to " + target);
            return new Assignment(a.target, src);
        } 
        if (s instanceof Conditional) {
            Conditional c = (Conditional)s;
            Expression test = T (c.test, tm);
            Statement tbr = T (c.thenbranch, tm);
            Statement ebr = T (c.elsebranch, tm);
            return new Conditional(test,  tbr, ebr);
        }
        if (s instanceof Loop) {
            Loop l = (Loop)s;
            Expression test = T (l.test, tm);
            Statement body = T (l.body, tm);
            return new Loop(test, body);
        }
        if (s instanceof Block) {
            Block b = (Block)s;
            Block out = new Block();
            for (Statement stmt : b.members)
                out.members.add(T(stmt, tm));
            return out;
        }
	if (s instanceof Return) {
		Return r = (Return) s;	
		return new Return(r.target, T(r.result, tm));
	}
	if (s instanceof CallStatement) {
		CallStatement c = (CallStatement) s;
		//Looking for typemap associated with call's name
		Object o = tm.get(new Variable(c.name));
		FunctionMap fm = (FunctionMap) o; 
		FunctionTypeMap called_params = (FunctionTypeMap) fm.getParams();	
		ArrayList<Type> param_types = called_params.typeArray(); 

		for (int i=0; i<c.args.size(); i++) {
			Type current_arg_type = StaticTypeCheck.typeOf(c.args.get(i), tm);
			if (param_types.get(i).equals(Type.FLOAT)) {
				if (current_arg_type.equals(Type.INT))
					c.args.set(i, new Unary(new Operator(Operator.I2F), T(c.args.get(i), tm)));	
				else
					c.args.set(i, T(c.args.get(i), tm));
			}
			else {
				c.args.set(i, T(c.args.get(i), tm));
			}
		} 
		return new CallStatement(c.name, c.args);
	}
	if (s instanceof Print) {
	    Print p = (Print) s;
	    return new Print(T(p.to_print, tm));
	}
        throw new IllegalArgumentException("should never reach here");
    }
    

    public static void main(String args[]) {
        Parser parser  = new Parser(new Lexer(args[0]));
        Program prog = parser.program();
        prog.display();           // student exercise
        System.out.println("\nBegin type checking...");
        System.out.println("Type map:");
        TypeMap map = StaticTypeCheck.typing(prog.globals, prog.functions);
        map.display();    // student exercise
        StaticTypeCheck.V(prog);
        Program out = T(prog, map);
        System.out.println("Output AST");
        out.display();    // student exercise
    } //main

    } // class TypeTransformer

    
