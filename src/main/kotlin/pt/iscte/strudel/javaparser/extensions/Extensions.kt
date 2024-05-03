package pt.iscte.strudel.javaparser.extensions

import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.CallableDeclaration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.ConstructorDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.comments.Comment
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.ExpressionStmt
import com.github.javaparser.ast.stmt.IfStmt
import com.github.javaparser.ast.stmt.Statement
import com.github.javaparser.ast.visitor.VoidVisitorAdapter
import pt.iscte.strudel.javaparser.INIT
import pt.iscte.strudel.javaparser.stringType
import pt.iscte.strudel.model.*
import pt.iscte.strudel.vm.IValue
import pt.iscte.strudel.vm.impl.Value
import java.util.*
import java.util.function.Predicate
import kotlin.jvm.optionals.getOrDefault

fun string(str: String): IValue = Value(stringType, java.lang.String(str))

val IModule.proceduresExcludingConstructors: List<IProcedureDeclaration>
  get() = procedures.filter { it.id != INIT }

val <T> Optional<T>.getOrNull: T?
  get() =
    if (isPresent) this.get() else null

fun Optional<Comment>.translateComment(): String? =
  if (isPresent) {
    val comment = get()
    val str = comment.asString()
    str.substring(comment.header?.length ?: 0, str.length - (comment.footer?.length ?: 0)).trim()
  } else null

val CallableDeclaration<*>.body: BlockStmt?
  get() = if (this is ConstructorDeclaration) this.body else (this as MethodDeclaration).body.getOrNull

internal val MethodCallExpr.isAbstractMethodCall: Boolean
  get() = kotlin.runCatching { resolve().isAbstract }.getOrDefault(false)

internal fun IProcedureDeclaration.matches(namespace: String?, id: String, parameterTypes: List<IType>): Boolean {
  val paramTypeMatch = this.parameters.map { it.type } == parameterTypes // FIXME
  val idAndNamespaceMatch =
    if (namespace == null) this.id == id
    else this.namespace == namespace && this.id == id
  return idAndNamespaceMatch // && paramTypeMatch
}

internal val ClassOrInterfaceDeclaration.qualifiedName: String
  get() = fullyQualifiedName.getOrDefault(nameAsString)

internal fun VariableDeclarator.isGeneric(type: ClassOrInterfaceDeclaration): Boolean {
  return typeAsString in type.typeParameters.map { it.nameAsString } || (type.findAncestor(ClassOrInterfaceDeclaration::class.java).getOrNull?.let {
    isGeneric(
      it
    )
  } ?: false)
}

internal fun VariableDeclarator.getGenericDeclarator(): ClassOrInterfaceDeclaration? =
  this.findAncestor(
    { typeAsString in it.typeParameters.map { type -> type.nameAsString } },
    ClassOrInterfaceDeclaration::class.java
  ).getOrNull

internal fun MethodDeclaration.replaceStringConcatPlus() {
  fun Expression.isStringType(): Boolean = calculateResolvedType().describe() == "java.lang.String"

  fun Expression.toStringExpression(): Expression =
    if (this is CharLiteralExpr) StringLiteralExpr(value)
    else if (isLiteralExpr && this !is StringLiteralExpr) StringLiteralExpr(toString())
    else run {
      val type = calculateResolvedType()
      if (isNameExpr && type.isReferenceType) MethodCallExpr(this, "toString")
      else if (isNameExpr && type.isPrimitive) MethodCallExpr(
        NameExpr(type.asPrimitive().boxTypeClass.simpleName),
        SimpleName("toString"),
        NodeList(this)
      )
      else if (type.isPrimitive) MethodCallExpr(NameExpr("String"), "valueOf", NodeList.nodeList(this))
      else MethodCallExpr(this, "toString")
    }

  val visitor = object : VoidVisitorAdapter<Any>() {
    override fun visit(n: BinaryExpr, arg: Any?) {
      if (n.operator == BinaryExpr.Operator.PLUS && (n.left.isStringType() || n.right.isStringType())) {
        val left = n.left.toStringExpression()
        val right = n.right.toStringExpression()
        val newCall = MethodCallExpr(left, "concat", NodeList.nodeList(right))
        n.replace(newCall)
        left.accept(this, arg)
        right.accept(this, arg)
      } else
        super.visit(n, arg)
    }
  }
  accept(visitor, null)
}


internal fun MethodDeclaration.replaceIncDecAsExpressions() {
  val injects = mutableListOf<Pair<Int, ExpressionStmt>>()

  body.ifPresent { body ->
    body.statements.forEachIndexed { index, statement ->
      statement.getSingleUnary().forEach { e ->
        if (e.operator.isPrefix)
          injects.add(Pair(index, ExpressionStmt(e.clone())))
        else if (e.operator.isPostfix)
          injects.add(Pair(index + 1, ExpressionStmt(e.clone())))

        e.replace(e.expression)
      }
    }

    injects.forEach {
      body.statements.add(it.first, it.second)
    }
  }
}

internal fun Statement.getSingleUnary(): List<UnaryExpr> =
  if (this is ExpressionStmt && this.expression is AssignExpr)
    findAll(UnaryExpr::class.java).filter { u ->
      //val p = u.findAncestor({ it.parentNode.getOrNull !is Expression },Expression::class.java)
      findAll(NameExpr::class.java).filter { n ->
        n == u.expression
      }.size == 1
    }
  else emptyList()