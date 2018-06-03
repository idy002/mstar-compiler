package Mstar.Worker.FrontEnd;

import Mstar.AST.*;
import Mstar.Symbol.VariableSymbol;

import java.util.*;

public class OutputIrrelevantEliminator implements IAstVisitor {
    private AstProgram astProgram;
    private HashSet<VariableSymbol> symbolRelevantSet;
    private HashMap<AstNode,HashSet<VariableSymbol>> usedSymbols;
    private HashMap<AstNode,HashSet<VariableSymbol>> definedSymbols;
    private boolean initSymbolStage;
    private boolean updateRelevantSet;
    private VariableSymbol functionCallIndicator;
    private VariableSymbol globalVariableIndicator;


    public OutputIrrelevantEliminator(AstProgram astProgram) {
        this.astProgram = astProgram;
        this.usedSymbols = new HashMap<>();
        this.definedSymbols = new HashMap<>();
        this.symbolRelevantSet = new HashSet<>();
        this.functionCallIndicator = new VariableSymbol(null, null, null,false, false);
        this.globalVariableIndicator = new VariableSymbol(null, null, null, false, false);
    }

    public void run() {
        System.err.println("Doing Output Irrelevant Elimination");
        for(FuncDeclaration funcDeclaration : astProgram.functions) {
            processFunction(funcDeclaration);
        }
        System.err.println("Output Irrelevant Elimination Finished");
    }

    private void processFunction(FuncDeclaration funcDeclaration) {
        usedSymbols.clear();
        definedSymbols.clear();
        symbolRelevantSet.clear();
        symbolRelevantSet.add(functionCallIndicator);
        symbolRelevantSet.add(globalVariableIndicator);

        initSymbolStage = true;
        funcDeclaration.accept(this);
        initSymbolStage = false;
        updateRelevantSet = true;
        int lastSize = -1;
        while(lastSize != symbolRelevantSet.size()) {
            lastSize = symbolRelevantSet.size();
            funcDeclaration.accept(this);
        }
        updateRelevantSet = false;

        funcDeclaration.accept(this);
    }

    private boolean canRemove(AstNode node) {
        HashSet<VariableSymbol> defined = new HashSet<>(definedSymbols.get(node));
        defined.retainAll(symbolRelevantSet);
        return defined.isEmpty();
    }

    @Override
    public void visit(AstProgram node) { }

    @Override
    public void visit(Declaration node) { }

    private void doRemove(List<Statement> list) {
        HashSet<Statement> needToRemove = new HashSet<>();
        for (Statement statement : list)
            if(canRemove(statement))
                needToRemove.add(statement);
        list.removeAll(needToRemove);
        for(Statement s : needToRemove) {
            AstPrinter astPrinter = new AstPrinter();
            s.accept(astPrinter);
            astPrinter.printTo(System.err);
        }
    }

    @Override
    public void visit(FuncDeclaration node) {
        if(initSymbolStage) {
            for (Statement statement : node.body)
                statement.accept(this);
        } else if(updateRelevantSet) {
            for(Statement statement : node.body)
                statement.accept(this);
        } else {
            doRemove(node.body);
        }
    }

    private void initSet(AstNode a) {
        definedSymbols.put(a, new HashSet<>());
        usedSymbols.put(a, new HashSet<>());
    }

    private void addDependence(AstNode a, AstNode b) {
        definedSymbols.get(a).addAll(definedSymbols.get(b));
        usedSymbols.get(a).addAll(usedSymbols.get(b));
    }

    private void propgate(AstNode a, AstNode... reqs) {
        propgate(a, Arrays.asList(reqs));
    }
    private void propgate(AstNode a, List<? extends AstNode> reqs) {
        if(!canRemove(a)) {
            for(AstNode req : reqs) {
                if(req == null) continue;
                symbolRelevantSet.addAll(usedSymbols.get(req));
            }
        }
    }

    @Override
    public void visit(ClassDeclaration node) { }
    @Override
    public void visit(VariableDeclaration node) {
        if(initSymbolStage) {
            initSet(node);
            if (node.init != null) {
                node.init.accept(this);
                addDependence(node, node.init);
            }
            definedSymbols.get(node).add(node.symbol);
        } else if(updateRelevantSet) {
            propgate(node, node.init);
            if(node.init != null)
                node.init.accept(this);
        }

    }

    @Override
    public void visit(TypeNode node) { }

    @Override
    public void visit(ArrayTypeNode node) { }

    @Override
    public void visit(PrimitiveTypeNode node) { }

    @Override
    public void visit(ClassTypeNode node) { }

    @Override
    public void visit(Statement node) { }

    @Override
    public void visit(ForStatement node) {
        if(initSymbolStage) {
            initSet(node);
            if(node.initStatement != null) {
                node.initStatement.accept(this);
                addDependence(node, node.initStatement);
            }
            if(node.condition != null) {
                node.condition.accept(this);
                addDependence(node, node.condition);
            }
            if(node.updateStatement != null) {
                node.updateStatement.accept(this);
                addDependence(node, node.updateStatement);
            }
            node.body.accept(this);
            addDependence(node, node.body);
        } else if(updateRelevantSet) {
            propgate(node, node.initStatement, node.condition, node.updateStatement);
            node.body.accept(this);
        } else {
            node.body.accept(this);
        }
    }

    @Override
    public void visit(WhileStatement node) {
        if(initSymbolStage) {
            initSet(node);
            node.condition.accept(this);
            addDependence(node, node.condition);
            node.body.accept(this);
            addDependence(node, node.body);
        } else if(updateRelevantSet) {
            propgate(node, node.condition);
        } else {
            node.body.accept(this);
        }
    }

    @Override
    public void visit(IfStatement node) {
        if(initSymbolStage) {
            initSet(node);
            node.condition.accept(this);
            addDependence(node, node.condition);
            node.thenStatement.accept(this);
            addDependence(node, node.thenStatement);
            if (node.elseStatement != null) {
                node.elseStatement.accept(this);
                addDependence(node, node.elseStatement);
            }
        } else if(updateRelevantSet) {
            propgate(node, node.condition);
            node.thenStatement.accept(this);
            if(node.elseStatement != null) node.elseStatement.accept(this);
        } else {
            node.thenStatement.accept(this);
            if(node.elseStatement != null)
                node.elseStatement.accept(this);
        }
    }

    @Override
    public void visit(ContinueStatement node) { }

    @Override
    public void visit(BreakStatement node) { }

    @Override
    public void visit(ReturnStatement node) {
        if(initSymbolStage) {
            initSet(node);
            if(node.retExpression != null) {
                node.retExpression.accept(this);
                addDependence(node, node.retExpression);
                symbolRelevantSet.addAll(usedSymbols.get(node.retExpression));
            }
            definedSymbols.get(node).add(globalVariableIndicator);
        } else if(updateRelevantSet) {
            if(node.retExpression != null)
                node.retExpression.accept(this);
        }
    }

    @Override
    public void visit(BlockStatement node) {
        if(initSymbolStage) {
            initSet(node);
            for (Statement statement : node.statements) {
                statement.accept(this);
                addDependence(node, statement);
            }
        } else if(updateRelevantSet) {
            for(Statement s : node.statements)
                s.accept(this);
        } else {
            doRemove(node.statements);
        }
    }

    @Override
    public void visit(VarDeclStatement node) {
        if(initSymbolStage) {
            initSet(node);
            node.declaration.accept(this);
            addDependence(node, node.declaration);
        } else if(updateRelevantSet) {
            propgate(node, node.declaration);
            node.declaration.accept(this);
        }
    }

    @Override
    public void visit(ExprStatement node) {
        if(initSymbolStage) {
            initSet(node);
            node.expression.accept(this);
            addDependence(node, node.expression);
        } else if(updateRelevantSet) {
            node.expression.accept(this);
        }
    }

    @Override
    public void visit(Expression node) { }

    @Override
    public void visit(Identifier node) {
        if(initSymbolStage) {
            initSet(node);
            if(node.symbol.isGlobalVariable) {
                definedSymbols.get(node).add(globalVariableIndicator);
            } else {
                usedSymbols.get(node).add(node.symbol);
            }
        }
    }

    @Override
    public void visit(LiteralExpression node) {
        if(initSymbolStage) {
            initSet(node);
        }
    }

    @Override
    public void visit(ArrayExpression node) {
        if(initSymbolStage) {
            initSet(node);
            node.address.accept(this);
            addDependence(node, node.address);
            node.index.accept(this);
            addDependence(node, node.index);
        }
    }

    @Override
    public void visit(FuncCallExpression node) {
        if(initSymbolStage) {
            initSet(node);
            definedSymbols.get(node).add(functionCallIndicator);
            for(Expression expression : node.arguments) {
                expression.accept(this);
                addDependence(node, expression);
            }
            symbolRelevantSet.addAll(usedSymbols.get(node));
        }
    }

    @Override
    public void visit(NewExpression node) {
        if(initSymbolStage) {
            initSet(node);
            for(Expression expression : node.exprDimensions) {
                expression.accept(this);
                addDependence(node, expression);
            }
        } else if(updateRelevantSet) {
            propgate(node, node.exprDimensions);
            for(Expression expression : node.exprDimensions) {
                expression.accept(this);
            }
        }
    }

    @Override
    public void visit(MemberExpression node) {
        if(initSymbolStage) {
            initSet(node);
            node.object.accept(this);
            addDependence(node, node.object);
        } else {
            propgate(node, node.object);
            node.object.accept(this);
        }
    }

    @Override
    public void visit(UnaryExpression node) {
        if(initSymbolStage) {
            initSet(node);
            node.expression.accept(this);
            addDependence(node, node.expression);
            if(node.op.contains("++") || node.op.contains("--"))
                definedSymbols.get(node).addAll(usedSymbols.get(node.expression));
        } else if(updateRelevantSet){
            propgate(node, node.expression);
            node.expression.accept(this);
        }
    }

    @Override
    public void visit(BinaryExpression node) {
        if(initSymbolStage) {
            initSet(node);
            node.lhs.accept(this);
            addDependence(node, node.lhs);
            node.rhs.accept(this);
            addDependence(node, node.rhs);
        } else if(updateRelevantSet){
            propgate(node, node.lhs, node.rhs);
            node.lhs.accept(this);
            node.rhs.accept(this);
        }
    }

    @Override
    public void visit(TernaryExpression node) {
        assert false;
    }

    @Override
    public void visit(AssignExpression node) {
        if(initSymbolStage) {
            initSet(node);
            node.lhs.accept(this);
            addDependence(node, node.lhs);
            definedSymbols.get(node).addAll(usedSymbols.get(node.lhs));
            node.rhs.accept(this);
            addDependence(node, node.rhs);
        } else if(updateRelevantSet) {
            propgate(node, node.lhs, node.rhs);
            node.lhs.accept(this);
            node.rhs.accept(this);
        }
    }

    @Override
    public void visit(EmptyStatement node) {
        if(initSymbolStage) {
            initSet(node);
        }
    }
}