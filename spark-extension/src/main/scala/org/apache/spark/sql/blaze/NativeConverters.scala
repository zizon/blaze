/*
 * Copyright 2022 The Blaze Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.blaze

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

import com.google.protobuf.ByteString
import org.apache.spark.SparkEnv

import org.apache.spark.internal.Logging
import org.apache.spark.sql.catalyst.expressions.Abs
import org.apache.spark.sql.catalyst.expressions.Acos
import org.apache.spark.sql.catalyst.expressions.Add
import org.apache.spark.sql.catalyst.expressions.And
import org.apache.spark.sql.catalyst.expressions.Asin
import org.apache.spark.sql.catalyst.expressions.Atan
import org.apache.spark.sql.catalyst.expressions.AttributeReference
import org.apache.spark.sql.catalyst.expressions.CaseWhen
import org.apache.spark.sql.catalyst.expressions.Cast
import org.apache.spark.sql.catalyst.expressions.Ceil
import org.apache.spark.sql.catalyst.expressions.Coalesce
import org.apache.spark.sql.catalyst.expressions.Concat
import org.apache.spark.sql.catalyst.expressions.Contains
import org.apache.spark.sql.catalyst.expressions.Cos
import org.apache.spark.sql.catalyst.expressions.Divide
import org.apache.spark.sql.catalyst.expressions.EndsWith
import org.apache.spark.sql.catalyst.expressions.EqualTo
import org.apache.spark.sql.catalyst.expressions.Exp
import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.catalyst.expressions.Floor
import org.apache.spark.sql.catalyst.expressions.GreaterThan
import org.apache.spark.sql.catalyst.expressions.GreaterThanOrEqual
import org.apache.spark.sql.catalyst.expressions.In
import org.apache.spark.sql.catalyst.expressions.InSet
import org.apache.spark.sql.catalyst.expressions.IsNotNull
import org.apache.spark.sql.catalyst.expressions.IsNull
import org.apache.spark.sql.catalyst.expressions.LessThan
import org.apache.spark.sql.catalyst.expressions.LessThanOrEqual
import org.apache.spark.sql.catalyst.expressions.Like
import org.apache.spark.sql.catalyst.expressions.Literal
import org.apache.spark.sql.catalyst.expressions.Log
import org.apache.spark.sql.catalyst.expressions.Log10
import org.apache.spark.sql.catalyst.expressions.Log2
import org.apache.spark.sql.catalyst.expressions.Lower
import org.apache.spark.sql.catalyst.expressions.Md5
import org.apache.spark.sql.catalyst.expressions.Multiply
import org.apache.spark.sql.catalyst.expressions.Not
import org.apache.spark.sql.catalyst.expressions.NullIf
import org.apache.spark.sql.catalyst.expressions.OctetLength
import org.apache.spark.sql.catalyst.expressions.Or
import org.apache.spark.sql.catalyst.expressions.Remainder
import org.apache.spark.sql.catalyst.expressions.Round
import org.apache.spark.sql.catalyst.expressions.Sha2
import org.apache.spark.sql.catalyst.expressions.Signum
import org.apache.spark.sql.catalyst.expressions.Sin
import org.apache.spark.sql.catalyst.expressions.Sqrt
import org.apache.spark.sql.catalyst.expressions.StartsWith
import org.apache.spark.sql.catalyst.expressions.StringTrim
import org.apache.spark.sql.catalyst.expressions.StringTrimLeft
import org.apache.spark.sql.catalyst.expressions.StringTrimRight
import org.apache.spark.sql.catalyst.expressions.Substring
import org.apache.spark.sql.catalyst.expressions.Subtract
import org.apache.spark.sql.catalyst.expressions.Tan
import org.apache.spark.sql.catalyst.expressions.TruncDate
import org.apache.spark.sql.catalyst.expressions.Upper
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression
import org.apache.spark.sql.catalyst.expressions.aggregate.Average
import org.apache.spark.sql.catalyst.expressions.aggregate.CollectList
import org.apache.spark.sql.catalyst.expressions.aggregate.CollectSet
import org.apache.spark.sql.catalyst.expressions.aggregate.Count
import org.apache.spark.sql.catalyst.expressions.aggregate.Max
import org.apache.spark.sql.catalyst.expressions.aggregate.Min
import org.apache.spark.sql.catalyst.expressions.aggregate.Sum
import org.apache.spark.sql.catalyst.expressions.Alias
import org.apache.spark.sql.catalyst.expressions.BitwiseAnd
import org.apache.spark.sql.catalyst.expressions.BitwiseOr
import org.apache.spark.sql.catalyst.expressions.BoundReference
import org.apache.spark.sql.catalyst.expressions.CheckOverflow
import org.apache.spark.sql.catalyst.expressions.CreateArray
import org.apache.spark.sql.catalyst.expressions.CreateNamedStruct
import org.apache.spark.sql.catalyst.expressions.GetArrayItem
import org.apache.spark.sql.catalyst.expressions.GetMapValue
import org.apache.spark.sql.catalyst.expressions.GetStructField
import org.apache.spark.sql.catalyst.expressions.If
import org.apache.spark.sql.catalyst.expressions.Length
import org.apache.spark.sql.catalyst.expressions.MakeDecimal
import org.apache.spark.sql.catalyst.expressions.Murmur3Hash
import org.apache.spark.sql.catalyst.expressions.Pmod
import org.apache.spark.sql.catalyst.expressions.PromotePrecision
import org.apache.spark.sql.catalyst.expressions.ShiftLeft
import org.apache.spark.sql.catalyst.expressions.ShiftRight
import org.apache.spark.sql.catalyst.expressions.Unevaluable
import org.apache.spark.sql.catalyst.expressions.UnscaledValue
import org.apache.spark.sql.catalyst.plans.FullOuter
import org.apache.spark.sql.catalyst.plans.Inner
import org.apache.spark.sql.catalyst.plans.JoinType
import org.apache.spark.sql.catalyst.plans.LeftAnti
import org.apache.spark.sql.catalyst.plans.LeftOuter
import org.apache.spark.sql.catalyst.plans.LeftSemi
import org.apache.spark.sql.catalyst.plans.RightOuter
import org.apache.spark.sql.execution.blaze.plan.Util
import org.apache.spark.sql.execution.ScalarSubquery
import org.apache.spark.sql.hive.blaze.HiveUDFUtil
import org.apache.spark.sql.hive.blaze.HiveUDFUtil.getFunctionClassName
import org.apache.spark.sql.hive.blaze.HiveUDFUtil.isHiveSimpleUDF
import org.apache.spark.sql.internal.SQLConf
import org.apache.spark.sql.types.ArrayType
import org.apache.spark.sql.types.AtomicType
import org.apache.spark.sql.types.BinaryType
import org.apache.spark.sql.types.BooleanType
import org.apache.spark.sql.types.ByteType
import org.apache.spark.sql.types.DataType
import org.apache.spark.sql.types.DateType
import org.apache.spark.sql.types.Decimal
import org.apache.spark.sql.types.DecimalType
import org.apache.spark.sql.types.DoubleType
import org.apache.spark.sql.types.FloatType
import org.apache.spark.sql.types.FractionalType
import org.apache.spark.sql.types.IntegerType
import org.apache.spark.sql.types.LongType
import org.apache.spark.sql.types.MapType
import org.apache.spark.sql.types.NullType
import org.apache.spark.sql.types.ShortType
import org.apache.spark.sql.types.StringType
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.types.TimestampType
import org.apache.spark.unsafe.types.UTF8String
import org.apache.spark.util.Utils
import org.blaze.{protobuf => pb}

object NativeConverters extends Logging {
  def convertToScalarType(dt: DataType): pb.PrimitiveScalarType = {
    dt match {
      case NullType => pb.PrimitiveScalarType.NULL
      case BooleanType => pb.PrimitiveScalarType.BOOL
      case ByteType => pb.PrimitiveScalarType.INT8
      case ShortType => pb.PrimitiveScalarType.INT16
      case IntegerType => pb.PrimitiveScalarType.INT32
      case LongType => pb.PrimitiveScalarType.INT64
      case FloatType => pb.PrimitiveScalarType.FLOAT32
      case DoubleType => pb.PrimitiveScalarType.FLOAT64
      case StringType => pb.PrimitiveScalarType.UTF8
      case DateType => pb.PrimitiveScalarType.DATE32
      case TimestampType => pb.PrimitiveScalarType.TIME_MICROSECOND
      case _: DecimalType => pb.PrimitiveScalarType.DECIMAL128
      case _: ArrayType => pb.PrimitiveScalarType.UTF8 // FIXME
      case _ => throw new NotImplementedError(s"convert $dt to DF scalar type not supported")
    }
  }

  def convertDataType(sparkDataType: DataType): pb.ArrowType = {
    val arrowTypeBuilder = pb.ArrowType.newBuilder()
    sparkDataType match {
      case NullType => arrowTypeBuilder.setNONE(pb.EmptyMessage.getDefaultInstance)
      case BooleanType => arrowTypeBuilder.setBOOL(pb.EmptyMessage.getDefaultInstance)
      case ByteType => arrowTypeBuilder.setINT8(pb.EmptyMessage.getDefaultInstance)
      case ShortType => arrowTypeBuilder.setINT16(pb.EmptyMessage.getDefaultInstance)
      case IntegerType => arrowTypeBuilder.setINT32(pb.EmptyMessage.getDefaultInstance)
      case LongType => arrowTypeBuilder.setINT64(pb.EmptyMessage.getDefaultInstance)
      case FloatType => arrowTypeBuilder.setFLOAT32(pb.EmptyMessage.getDefaultInstance)
      case DoubleType => arrowTypeBuilder.setFLOAT64(pb.EmptyMessage.getDefaultInstance)
      case StringType => arrowTypeBuilder.setUTF8(pb.EmptyMessage.getDefaultInstance)
      case BinaryType => arrowTypeBuilder.setBINARY(pb.EmptyMessage.getDefaultInstance)
      case DateType => arrowTypeBuilder.setDATE32(pb.EmptyMessage.getDefaultInstance)

      // timezone is never used in native side
      case TimestampType =>
        arrowTypeBuilder.setTIMESTAMP(
          pb.Timestamp
            .newBuilder()
            .setTimeUnit(pb.TimeUnit.Microsecond))

      // decimal
      case t: DecimalType =>
        arrowTypeBuilder.setDECIMAL(
          org.blaze.protobuf.Decimal
            .newBuilder()
            .setWhole(Math.max(t.precision, 1))
            .setFractional(t.scale)
            .build())

      // array/list
      case a: ArrayType =>
        typedCheckChildTypeNested(a.elementType)
        arrowTypeBuilder.setLIST(
          org.blaze.protobuf.List
            .newBuilder()
            .setFieldType(
              pb.Field
                .newBuilder()
                .setName("item")
                .setArrowType(convertDataType(a.elementType))
                .setNullable(a.containsNull))
            .build())

      case m: MapType =>
        typedCheckChildTypeNested(m.keyType)
        typedCheckChildTypeNested(m.valueType)
        arrowTypeBuilder.setMAP(
          org.blaze.protobuf.Map
            .newBuilder()
            .setKeyType(
              pb.Field
                .newBuilder()
                .setName("key")
                .setArrowType(convertDataType(m.keyType))
                .setNullable(false))
            .setValueType(
              pb.Field
                .newBuilder()
                .setName("value")
                .setArrowType(convertDataType(m.valueType))
                .setNullable(m.valueContainsNull))
            .build())
      case s: StructType =>
        s.fields.foreach(field => typedCheckChildTypeNested(field.dataType))
        arrowTypeBuilder.setSTRUCT(
          org.blaze.protobuf.Struct
            .newBuilder()
            .addAllSubFieldTypes(
              s.fields
                .map(
                  e =>
                    pb.Field
                      .newBuilder()
                      .setArrowType(convertDataType(e.dataType))
                      .setName(e.name)
                      .setNullable(e.nullable)
                      .build())
                .toList
                .asJava)
            .build())

      case _ =>
        throw new NotImplementedError(s"Data type conversion not implemented ${sparkDataType}")
    }
    arrowTypeBuilder.build()
  }

  def convertValue(sparkValue: Any, dataType: DataType): pb.ScalarValue = {
    val scalarValueBuilder = pb.ScalarValue.newBuilder()
    dataType match {
      case NullType => scalarValueBuilder.setNullValue(pb.PrimitiveScalarType.NULL)
      case BooleanType => scalarValueBuilder.setBoolValue(sparkValue.asInstanceOf[Boolean])
      case ByteType => scalarValueBuilder.setInt8Value(sparkValue.asInstanceOf[Byte])
      case ShortType => scalarValueBuilder.setInt16Value(sparkValue.asInstanceOf[Short])
      case IntegerType => scalarValueBuilder.setInt32Value(sparkValue.asInstanceOf[Int])
      case LongType => scalarValueBuilder.setInt64Value(sparkValue.asInstanceOf[Long])
      case FloatType => scalarValueBuilder.setFloat32Value(sparkValue.asInstanceOf[Float])
      case DoubleType => scalarValueBuilder.setFloat64Value(sparkValue.asInstanceOf[Double])
      case StringType => scalarValueBuilder.setUtf8Value(sparkValue.toString)
      case BinaryType => throw new NotImplementedError("BinaryType not yet supported")
      case DateType => scalarValueBuilder.setDate32Value(sparkValue.asInstanceOf[Int])
      case TimestampType =>
        scalarValueBuilder.setTimeMicrosecondValue(sparkValue.asInstanceOf[Long])
      case t: DecimalType =>
        val decimalValue = sparkValue.asInstanceOf[Decimal]
        val decimalType = convertDataType(t).getDECIMAL
        scalarValueBuilder.setDecimalValue(
          pb.ScalarDecimalValue
            .newBuilder()
            .setDecimal(decimalType)
            .setLongValue(decimalValue.toUnscaledLong))

      // TODO: support complex data types
      case _: ArrayType | _: StructType | _: MapType if sparkValue == null =>
        pb.PrimitiveScalarType.NULL
      case _ => throw new NotImplementedError(s"Value conversion not implemented ${dataType}")
    }

    // TODO: support complex data types
    scalarValueBuilder.build()
  }

  def convertField(sparkField: StructField): pb.Field = {
    pb.Field
      .newBuilder()
      .setName(sparkField.name)
      .setNullable(sparkField.nullable)
      .setArrowType(convertDataType(sparkField.dataType))
      .build()
  }

  def convertSchema(sparkSchema: StructType): pb.Schema = {
    val schemaBuilder = pb.Schema.newBuilder()
    sparkSchema.foreach(sparkField => schemaBuilder.addColumns(convertField(sparkField)))
    schemaBuilder.build()
  }

  case class NativeExprWrapper(
      wrapped: pb.PhysicalExprNode,
      override val dataType: DataType = NullType,
      override val nullable: Boolean = true,
      override val children: Seq[Expression] = Nil)
      extends Unevaluable

  def convertExpr(sparkExpr: Expression, useAttrExprId: Boolean = true): pb.PhysicalExprNode = {
    assert(sparkExpr.deterministic, s"nondeterministic expression not supported: $sparkExpr")

    def buildExprNode(buildFn: pb.PhysicalExprNode.Builder => pb.PhysicalExprNode.Builder)
        : pb.PhysicalExprNode =
      buildFn(pb.PhysicalExprNode.newBuilder()).build()

    def buildBinaryExprNode(
        left: Expression,
        right: Expression,
        op: String): pb.PhysicalExprNode =
      buildExprNode {
        _.setBinaryExpr(
          pb.PhysicalBinaryExprNode
            .newBuilder()
            .setL(convertExpr(left, useAttrExprId))
            .setR(convertExpr(right, useAttrExprId))
            .setOp(op))
      }

    def buildScalarFunction(
        fn: pb.ScalarFunction,
        args: Seq[Expression],
        dataType: DataType): pb.PhysicalExprNode =
      buildExprNode {
        _.setScalarFunction(
          pb.PhysicalScalarFunctionNode
            .newBuilder()
            .setName(fn.name())
            .setFun(fn)
            .addAllArgs(args.map(expr => convertExpr(expr, useAttrExprId)).asJava)
            .setReturnType(convertDataType(dataType)))
      }

    def buildExtScalarFunction(
        name: String,
        args: Seq[Expression],
        dataType: DataType): pb.PhysicalExprNode =
      buildExprNode {
        _.setScalarFunction(
          pb.PhysicalScalarFunctionNode
            .newBuilder()
            .setName(name)
            .setFun(pb.ScalarFunction.SparkExtFunctions)
            .addAllArgs(args.map(expr => convertExpr(expr, useAttrExprId)).asJava)
            .setReturnType(convertDataType(dataType)))
      }

    def unpackBinaryTypeCast(expr: Expression) =
      expr match {
        case Cast(inner, BinaryType, _) => inner
        case expr => expr
      }

    sparkExpr match {
      case NativeExprWrapper(wrapped, _, _, _) =>
        wrapped
      case l @ Literal(value, dataType) =>
        buildExprNode { b =>
          if (value == null) {
            dataType match {
              case at: ArrayType =>
                b.setTryCast(
                  pb.PhysicalTryCastNode
                    .newBuilder()
                    .setArrowType(convertDataType(at))
                    .setExpr(buildExprNode {
                      _.setLiteral(
                        pb.ScalarValue.newBuilder().setNullValue(convertToScalarType(NullType)))
                    }))
              case _ =>
                b.setLiteral(
                  pb.ScalarValue.newBuilder().setNullValue(convertToScalarType(dataType)))
            }
          } else {
            b.setLiteral(convertValue(value, dataType))
          }
        }
      case ar: AttributeReference =>
        buildExprNode {
          if (useAttrExprId) {
            _.setColumn(
              pb.PhysicalColumn.newBuilder().setName(Util.getFieldNameByExprId(ar)).build())
          } else {
            _.setColumn(pb.PhysicalColumn.newBuilder().setName(ar.name).build())
          }
        }

      case alias: Alias =>
        convertExpr(alias.child, useAttrExprId)

      // ScalarSubquery
      case subquery: ScalarSubquery =>
        subquery.updateResult()
        val value = Literal.create(subquery.eval(null), subquery.dataType)
        convertExpr(value, useAttrExprId)

      // cast
      // not performing native cast for timestamps (will use UDFWrapper instead)
      case Cast(child, dataType, _) if !dataType.isInstanceOf[TimestampType] =>
        buildExprNode {
          _.setTryCast(
            pb.PhysicalTryCastNode
              .newBuilder()
              .setExpr(convertExpr(child, useAttrExprId))
              .setArrowType(convertDataType(dataType))
              .build())
        }

      // in
      case In(value, list) if list.forall(_.isInstanceOf[Literal]) =>
        buildExprNode {
          _.setInList(
            pb.PhysicalInListNode
              .newBuilder()
              .setExpr(convertExpr(value, useAttrExprId))
              .addAllList(list.map(expr => convertExpr(expr, useAttrExprId)).asJava))
        }

      // in
      case InSet(value, set) if set.forall(_.isInstanceOf[Literal]) =>
        buildExprNode {
          _.setInList(
            pb.PhysicalInListNode
              .newBuilder()
              .setExpr(convertExpr(value, useAttrExprId))
              .addAllList(set.map {
                case utf8string: UTF8String =>
                  convertExpr(Literal(utf8string, StringType), useAttrExprId)
                case v => convertExpr(Literal.apply(v), useAttrExprId)
              }.asJava))
        }

      // unary ops
      case IsNull(child) =>
        buildExprNode {
          _.setIsNullExpr(
            pb.PhysicalIsNull.newBuilder().setExpr(convertExpr(child, useAttrExprId)).build())
        }
      case IsNotNull(child) =>
        buildExprNode {
          _.setIsNotNullExpr(
            pb.PhysicalIsNotNull.newBuilder().setExpr(convertExpr(child, useAttrExprId)).build())
        }
      case Not(EqualTo(lhs, rhs)) => buildBinaryExprNode(lhs, rhs, "NotEq")
      case Not(child) =>
        buildExprNode {
          _.setNotExpr(
            pb.PhysicalNot.newBuilder().setExpr(convertExpr(child, useAttrExprId)).build())
        }

      // binary ops
      case EqualTo(lhs, rhs) => buildBinaryExprNode(lhs, rhs, "Eq")
      case GreaterThan(lhs, rhs) => buildBinaryExprNode(lhs, rhs, "Gt")
      case LessThan(lhs, rhs) => buildBinaryExprNode(lhs, rhs, "Lt")
      case GreaterThanOrEqual(lhs, rhs) => buildBinaryExprNode(lhs, rhs, "GtEq")
      case LessThanOrEqual(lhs, rhs) => buildBinaryExprNode(lhs, rhs, "LtEq")

      case e @ Add(lhs, rhs) =>
        if (lhs.dataType.isInstanceOf[DecimalType] || rhs.dataType.isInstanceOf[DecimalType]) {
          val resultType = arithDecimalReturnType(e)
          val left = Cast(lhs, DoubleType)
          val right = Cast(rhs, DoubleType)
          buildExprNode {
            _.setTryCast(
              pb.PhysicalTryCastNode
                .newBuilder()
                .setExpr(convertExpr(Add.apply(left, right), useAttrExprId))
                .setArrowType(convertDataType(resultType))
                .build())
          }
        } else {
          buildBinaryExprNode(lhs, rhs, "Plus")
        }

      case e @ Subtract(lhs, rhs) =>
        if (lhs.dataType.isInstanceOf[DecimalType] || rhs.dataType.isInstanceOf[DecimalType]) {
          val resultType = arithDecimalReturnType(e)
          val left = Cast(lhs, DoubleType)
          val right = Cast(rhs, DoubleType)
          buildExprNode {
            _.setTryCast(
              pb.PhysicalTryCastNode
                .newBuilder()
                .setExpr(convertExpr(Subtract.apply(left, right), useAttrExprId))
                .setArrowType(convertDataType(resultType))
                .build())
          }
        } else {
          buildBinaryExprNode(lhs, rhs, "Minus")
        }

      case e @ Multiply(lhs, rhs) =>
        if (lhs.dataType.isInstanceOf[DecimalType] || rhs.dataType.isInstanceOf[DecimalType]) {
          val resultType = arithDecimalReturnType(e)
          val left = Cast(lhs, DoubleType)
          val right = Cast(rhs, DoubleType)
          buildExprNode {
            _.setTryCast(
              pb.PhysicalTryCastNode
                .newBuilder()
                .setExpr(convertExpr(Multiply.apply(left, right), useAttrExprId))
                .setArrowType(convertDataType(resultType))
                .build())
          }
        } else {
          buildBinaryExprNode(lhs, rhs, "Multiply")
        }

      case e @ Divide(lhs, rhs) =>
        if (lhs.dataType.isInstanceOf[DecimalType] || rhs.dataType.isInstanceOf[DecimalType]) {
          val resultType = arithDecimalReturnType(e)
          val left = Cast(lhs, DoubleType)
          val right = Cast(rhs, DoubleType)
          buildExprNode {
            _.setTryCast(
              pb.PhysicalTryCastNode
                .newBuilder()
                .setExpr(convertExpr(Divide.apply(left, right), useAttrExprId))
                .setArrowType(convertDataType(resultType))
                .build())
          }
        } else {
          buildBinaryExprNode(lhs, rhs, "Divide")
        }

      case e @ Remainder(lhs, rhs) =>
        val resultType = arithDecimalReturnType(e)
        rhs match {
          case rhs: Literal if rhs == Literal.default(rhs.dataType) =>
            buildExprNode(_.setLiteral(convertValue(null, e.dataType)))
          case rhs: Literal if rhs != Literal.default(rhs.dataType) =>
            buildBinaryExprNode(lhs, rhs, "Modulo")
          case rhs =>
            val l = convertExpr(Cast(lhs, resultType), useAttrExprId)
            val r =
              buildExtScalarFunction("NullIfZero", Cast(rhs, resultType) :: Nil, rhs.dataType)

            buildExprNode {
              _.setBinaryExpr(
                pb.PhysicalBinaryExprNode.newBuilder().setL(l).setR(r).setOp("Modulo"))
            }
        }
      case e: Like =>
        assert(Shims.get.exprShims.getEscapeChar(e) == '\\')
        buildExprNode {
          _.setLikeExpr(
            pb.PhysicalLikeExprNode
              .newBuilder()
              .setNegated(false)
              .setCaseInsensitive(false)
              .setExpr(convertExpr(e.left))
              .setPattern(convertExpr(e.right)))
        }

      // if rhs is complex in and/or operators, use short-circuiting implementation
      case e @ And(lhs, rhs) if rhs.find(HiveUDFUtil.isHiveUDF).isDefined =>
        buildExprNode {
          _.setSparkLogicalExpr(
            pb.SparkLogicalExprNode
              .newBuilder()
              .setArg1(convertExpr(lhs, useAttrExprId))
              .setArg2(convertExpr(rhs, useAttrExprId))
              .setOp("And"))
        }
      case e @ Or(lhs, rhs) if rhs.find(HiveUDFUtil.isHiveUDF).isDefined =>
        buildExprNode {
          _.setSparkLogicalExpr(
            pb.SparkLogicalExprNode
              .newBuilder()
              .setArg1(convertExpr(lhs, useAttrExprId))
              .setArg2(convertExpr(rhs, useAttrExprId))
              .setOp("Or"))
        }
      case And(lhs, rhs) => buildBinaryExprNode(lhs, rhs, "And")
      case Or(lhs, rhs) => buildBinaryExprNode(lhs, rhs, "Or")

      // bitwise
      case BitwiseAnd(lhs, rhs) => buildBinaryExprNode(lhs, rhs, "BitwiseAnd")
      case BitwiseOr(lhs, rhs) => buildBinaryExprNode(lhs, rhs, "BitwiseOr")
      case ShiftLeft(lhs, rhs) => buildBinaryExprNode(lhs, rhs, "BitwiseShiftLeft")
      case ShiftRight(lhs, rhs) => buildBinaryExprNode(lhs, rhs, "BitwiseShiftRight")

      // builtin scalar functions
      case e: Sqrt => buildScalarFunction(pb.ScalarFunction.Sqrt, e.children, e.dataType)
      case e: Sin => buildScalarFunction(pb.ScalarFunction.Sin, e.children, e.dataType)
      case e: Cos => buildScalarFunction(pb.ScalarFunction.Cos, e.children, e.dataType)
      case e: Tan => buildScalarFunction(pb.ScalarFunction.Tan, e.children, e.dataType)
      case e: Asin => buildScalarFunction(pb.ScalarFunction.Asin, e.children, e.dataType)
      case e: Acos => buildScalarFunction(pb.ScalarFunction.Acos, e.children, e.dataType)
      case e: Atan => buildScalarFunction(pb.ScalarFunction.Atan, e.children, e.dataType)
      case e: Exp => buildScalarFunction(pb.ScalarFunction.Exp, e.children, e.dataType)
      case e: Log => buildScalarFunction(pb.ScalarFunction.Log, e.children, e.dataType)
      case e: Log2 => buildScalarFunction(pb.ScalarFunction.Log2, e.children, e.dataType)
      case e: Log10 => buildScalarFunction(pb.ScalarFunction.Log10, e.children, e.dataType)
      case e: Floor =>
        buildExprNode {
          _.setTryCast(
            pb.PhysicalTryCastNode
              .newBuilder()
              .setExpr(buildScalarFunction(pb.ScalarFunction.Floor, e.children, e.dataType))
              .setArrowType(convertDataType(e.dataType))
              .build())
        }
      case e: Ceil =>
        buildExprNode {
          _.setTryCast(
            pb.PhysicalTryCastNode
              .newBuilder()
              .setExpr(buildScalarFunction(pb.ScalarFunction.Ceil, e.children, e.dataType))
              .setArrowType(convertDataType(e.dataType))
              .build())
        }
      case e @ Round(_1, Literal(0, _)) if _1.dataType.isInstanceOf[FractionalType] =>
        buildScalarFunction(pb.ScalarFunction.Round, Seq(_1), e.dataType)
      case e @ Round(_1, Literal(n: Int, _))
          if _1.dataType.isInstanceOf[FractionalType] && n >= 0 =>
        buildExtScalarFunction("RoundN", Seq(_1, Literal(n, IntegerType)), e.dataType)

      case e: Signum => buildScalarFunction(pb.ScalarFunction.Signum, e.children, e.dataType)
      case e: Abs if e.dataType.isInstanceOf[FloatType] || e.dataType.isInstanceOf[DoubleType] =>
        buildScalarFunction(pb.ScalarFunction.Abs, e.children, e.dataType)
      case e: OctetLength =>
        buildScalarFunction(pb.ScalarFunction.OctetLength, e.children, e.dataType)
      case Length(arg) if arg.dataType == StringType =>
        buildScalarFunction(pb.ScalarFunction.CharacterLength, arg :: Nil, IntegerType)
      case e: Concat => buildScalarFunction(pb.ScalarFunction.Concat, e.children, e.dataType)
      case e: Lower => buildScalarFunction(pb.ScalarFunction.Lower, e.children, e.dataType)
      case e: Upper => buildScalarFunction(pb.ScalarFunction.Upper, e.children, e.dataType)
      case e: StringTrim =>
        buildScalarFunction(pb.ScalarFunction.Trim, e.srcStr +: e.trimStr.toSeq, e.dataType)
      case e: StringTrimLeft =>
        buildScalarFunction(pb.ScalarFunction.Ltrim, e.srcStr +: e.trimStr.toSeq, e.dataType)
      case e: StringTrimRight =>
        buildScalarFunction(pb.ScalarFunction.Rtrim, e.srcStr +: e.trimStr.toSeq, e.dataType)
      case e @ NullIf(left, right, _) =>
        buildScalarFunction(pb.ScalarFunction.NullIf, left :: right :: Nil, e.dataType)
      case e: TruncDate =>
        buildScalarFunction(pb.ScalarFunction.DateTrunc, e.children, e.dataType)
      case Md5(_1) =>
        buildScalarFunction(pb.ScalarFunction.MD5, Seq(unpackBinaryTypeCast(_1)), StringType)
      case Sha2(_1, Literal(224, _)) =>
        buildScalarFunction(pb.ScalarFunction.SHA224, Seq(unpackBinaryTypeCast(_1)), StringType)
      case Sha2(_1, Literal(0, _)) =>
        buildScalarFunction(pb.ScalarFunction.SHA256, Seq(unpackBinaryTypeCast(_1)), StringType)
      case Sha2(_1, Literal(256, _)) =>
        buildScalarFunction(pb.ScalarFunction.SHA256, Seq(unpackBinaryTypeCast(_1)), StringType)
      case Sha2(_1, Literal(384, _)) =>
        buildScalarFunction(pb.ScalarFunction.SHA384, Seq(unpackBinaryTypeCast(_1)), StringType)
      case Sha2(_1, Literal(512, _)) =>
        buildScalarFunction(pb.ScalarFunction.SHA512, Seq(unpackBinaryTypeCast(_1)), StringType)
      case Murmur3Hash(children, 42) =>
        buildExtScalarFunction("Murmur3Hash", children, IntegerType)

      case StartsWith(expr, Literal(prefix, StringType)) =>
        buildExprNode(
          _.setStringStartsWithExpr(
            pb.StringStartsWithExprNode
              .newBuilder()
              .setExpr(convertExpr(expr, useAttrExprId))
              .setPrefix(prefix.toString)))

      case EndsWith(expr, Literal(suffix, StringType)) =>
        buildExprNode(
          _.setStringEndsWithExpr(
            pb.StringEndsWithExprNode
              .newBuilder()
              .setExpr(convertExpr(expr, useAttrExprId))
              .setSuffix(suffix.toString)))

      case Contains(expr, Literal(infix, StringType)) =>
        buildExprNode(
          _.setStringContainsExpr(
            pb.StringContainsExprNode
              .newBuilder()
              .setExpr(convertExpr(expr, useAttrExprId))
              .setInfix(infix.toString)))

      case Substring(str, Literal(pos, IntegerType), Literal(len, IntegerType))
          if pos.asInstanceOf[Int] >= 0 && len.asInstanceOf[Int] >= 0 =>
        buildScalarFunction(
          pb.ScalarFunction.Substr,
          str :: Literal(pos.asInstanceOf[Long]) :: Literal(len.asInstanceOf[Long]) :: Nil,
          StringType)

      case e: Coalesce => buildScalarFunction(pb.ScalarFunction.Coalesce, e.children, e.dataType)

      case e @ If(predicate, trueValue, falseValue) =>
        buildExprNode {
          _.setIifExpr(
            pb.IIfExprNode
              .newBuilder()
              .setCondition(convertExpr(predicate, useAttrExprId))
              .setTruthy(convertExpr(trueValue, useAttrExprId))
              .setFalsy(convertExpr(falseValue, useAttrExprId))
              .setDataType(convertDataType(e.dataType)))
        }
      case CaseWhen(branches, elseValue) =>
        val caseExpr = pb.PhysicalCaseNode.newBuilder()
        val whenThens = branches.map {
          case (w, t) =>
            val whenThen = pb.PhysicalWhenThen.newBuilder()
            whenThen.setWhenExpr(convertExpr(w, useAttrExprId))
            whenThen.setThenExpr(convertExpr(t, useAttrExprId))
            whenThen.build()
        }
        caseExpr.addAllWhenThenExpr(whenThens.asJava)
        elseValue.foreach(el => caseExpr.setElseExpr(convertExpr(el, useAttrExprId)))
        pb.PhysicalExprNode.newBuilder().setCase(caseExpr).build()

      // expressions for DecimalPrecision rule
      case UnscaledValue(_1) =>
        val args = _1 :: Nil
        buildExtScalarFunction("UnscaledValue", args, LongType)

      case e: MakeDecimal =>
        // case MakeDecimal(_1, precision, scale) =>
        //  assert(!SQLConf.get.ansiEnabled)
        val precision = e.precision
        val scale = e.scale
        val args =
          e.child :: Literal
            .apply(precision, IntegerType) :: Literal.apply(scale, IntegerType) :: Nil
        buildExtScalarFunction("MakeDecimal", args, DecimalType(precision, scale))

      case PromotePrecision(_1) =>
        _1 match {
          case Cast(_, dt, _) if dt == _1.dataType =>
            convertExpr(_1, useAttrExprId)
          case _ =>
            convertExpr(Cast(_1, _1.dataType), useAttrExprId)
        }
      case e: CheckOverflow =>
        // case CheckOverflow(_1, DecimalType(precision, scale)) =>
        val precision = e.dataType.precision
        val scale = e.dataType.scale
        val args =
          e.child :: Literal
            .apply(precision, IntegerType) :: Literal.apply(scale, IntegerType) :: Nil
        buildExtScalarFunction("CheckOverflow", args, DecimalType(precision, scale))

      // aggr
      // aggr add new parameter filter
      case e: AggregateExpression =>
        assert(Shims.get.exprShims.getAggregateExpressionFilter(e).isEmpty)
        val aggBuilder = pb.PhysicalAggExprNode
          .newBuilder()

        e.aggregateFunction match {
          case e @ Max(child) if e.dataType.isInstanceOf[AtomicType] =>
            aggBuilder.setAggFunction(pb.AggFunction.MAX)
            aggBuilder.addChildren(convertExpr(child, useAttrExprId))
          case e @ Min(child) if e.dataType.isInstanceOf[AtomicType] =>
            aggBuilder.setAggFunction(pb.AggFunction.MIN)
            aggBuilder.addChildren(convertExpr(child, useAttrExprId))
          case e @ Sum(child) if e.dataType.isInstanceOf[AtomicType] =>
            aggBuilder.setAggFunction(pb.AggFunction.SUM)
            aggBuilder.addChildren(convertExpr(child, useAttrExprId))
          case e @ Average(child) if e.dataType.isInstanceOf[AtomicType] =>
            aggBuilder.setAggFunction(pb.AggFunction.AVG)
            aggBuilder.addChildren(convertExpr(child, useAttrExprId))
          case Count(Seq(child1)) =>
            aggBuilder.setAggFunction(pb.AggFunction.COUNT)
            aggBuilder.addChildren(convertExpr(child1, useAttrExprId))
          case Count(children) if !children.exists(_.nullable) =>
            aggBuilder.setAggFunction(pb.AggFunction.COUNT)
            aggBuilder.addChildren(convertExpr(Literal.apply(1), useAttrExprId))
          case CollectList(child, _, _) =>
            aggBuilder.setAggFunction(pb.AggFunction.COLLECT_LIST)
            aggBuilder.addChildren(convertExpr(child, useAttrExprId))
          case CollectSet(child, _, _) =>
            aggBuilder.setAggFunction(pb.AggFunction.COLLECT_SET)
            aggBuilder.addChildren(convertExpr(child, useAttrExprId))
        }
        pb.PhysicalExprNode
          .newBuilder()
          .setAggExpr(aggBuilder)
          .build()

      case e: CreateArray => buildExtScalarFunction("MakeArray", e.children, e.dataType)

      case e: CreateNamedStruct =>
        val NameVec = new ArrayBuffer[String]()
        val ValuesVec = new ArrayBuffer[Expression]()
        for (Seq(name, value) <- e.children.grouped(2)) {
          assert(name.isInstanceOf[Literal])
          NameVec += name.toString()
          ValuesVec += value
        }

        buildExprNode {
          _.setNamedStruct(
            pb.PhysicalNamedStructExprNode
              .newBuilder()
              .addAllNames(NameVec.toList.asJava)
              .addAllValues(ValuesVec.map(value => convertExpr(value)).toList.asJava)
              .setReturnType(convertDataType(e.dataType)))
        }

      case GetArrayItem(child, Literal(ordinalValue: Number, _)) =>
        buildExprNode {
          _.setGetIndexedFieldExpr(
            pb.PhysicalGetIndexedFieldExprNode
              .newBuilder()
              .setExpr(convertExpr(child))
              .setKey(convertValue(
                ordinalValue.longValue() + 1, // NOTE: data-fusion index starts from 1
                LongType)))
        }

      case GetMapValue(child, Literal(value, dataType)) =>
        buildExprNode {
          _.setGetMapValueExpr(
            pb.PhysicalGetMapValueExprNode
              .newBuilder()
              .setExpr(convertExpr(child))
              .setKey(convertValue(value, dataType)))
        }

      case GetStructField(child, _, name) =>
        buildExprNode {
          _.setGetIndexedFieldExpr(
            pb.PhysicalGetIndexedFieldExprNode
              .newBuilder()
              .setExpr(convertExpr(child))
              .setKey(convertValue(name.get, StringType)))
        }

      // hive UDFJson
      case e
          if (isHiveSimpleUDF(e)
            && getFunctionClassName(e).contains("org.apache.hadoop.hive.ql.udf.UDFJson")
            && SparkEnv.get.conf.getBoolean(
              "spark.blaze.udf.UDFJson.enabled",
              defaultValue = true)
            && e.children.length == 2
            && e.children(0).dataType == StringType
            && e.children(1).dataType == StringType
            && e.children(1).isInstanceOf[Literal]) =>
        buildExtScalarFunction("GetJsonObject", e.children, StringType)

      // hive UDF and other unsupported expressions
      case e =>
        val bounded = e.withNewChildren(e.children.zipWithIndex.map {
          case (param, index) => BoundReference(index, param.dataType, param.nullable)
        })
        val serialized = serializeExpression(bounded.asInstanceOf[Expression with Serializable])
        logWarning(s"expression is not supported in blaze and fallbacks to spark: $e")

        pb.PhysicalExprNode
          .newBuilder()
          .setSparkUdfWrapperExpr(
            pb.PhysicalSparkUDFWrapperExprNode
              .newBuilder()
              .setSerialized(ByteString.copyFrom(serialized))
              .setReturnType(convertDataType(bounded.dataType))
              .setReturnNullable(bounded.nullable)
              .addAllParams(e.children.map(expr => convertExpr(expr, useAttrExprId)).asJava))
          .build()
    }
  }

  def convertJoinType(joinType: JoinType): org.blaze.protobuf.JoinType = {
    joinType match {
      case Inner => org.blaze.protobuf.JoinType.INNER
      case LeftOuter => org.blaze.protobuf.JoinType.LEFT
      case RightOuter => org.blaze.protobuf.JoinType.RIGHT
      case FullOuter => org.blaze.protobuf.JoinType.FULL
      case LeftSemi => org.blaze.protobuf.JoinType.SEMI
      case LeftAnti => org.blaze.protobuf.JoinType.ANTI
      case _ => throw new NotImplementedError(s"unsupported join type: ${joinType}")
    }
  }

  def serializeExpression(udf: Expression with Serializable): Array[Byte] = {
    Utils.tryWithResource(new ByteArrayOutputStream()) { bos =>
      Utils.tryWithResource(new ObjectOutputStream(bos)) { oos =>
        oos.writeObject(udf)
        null
      }
      bos.toByteArray
    }
  }

  def deserializeExpression(serialized: Array[Byte]): Expression with Serializable = {
    Utils.tryWithResource(new ByteArrayInputStream(serialized)) { bis =>
      Utils.tryWithResource(new ObjectInputStream(bis)) { ois =>
        ois.readObject().asInstanceOf[Expression with Serializable]
      }
    }
  }

  def typedCheckChildTypeNested(dt: DataType): Unit = {
    if (dt.isInstanceOf[ArrayType] || dt.isInstanceOf[MapType] || dt.isInstanceOf[StructType]) {
      throw new NotImplementedError(
        s"Data type conversion not implemented for nesting type with child type: ${dt.simpleString}")
    }
  }

  private def arithDecimalReturnType(e: Expression): DataType = {
    import scala.math.max
    import scala.math.min
    e match {
      case Add(e1 @ DecimalType.Expression(p1, s1), e2 @ DecimalType.Expression(p2, s2)) =>
        val resultScale = max(s1, s2)
        if (SQLConf.get.decimalOperationsAllowPrecisionLoss) {
          DecimalType.adjustPrecisionScale(max(p1 - s1, p2 - s2) + resultScale + 1, resultScale)
        } else {
          DecimalType.bounded(max(p1 - s1, p2 - s2) + resultScale + 1, resultScale)
        }

      case Subtract(e1 @ DecimalType.Expression(p1, s1), e2 @ DecimalType.Expression(p2, s2)) =>
        val resultScale = max(s1, s2)
        if (SQLConf.get.decimalOperationsAllowPrecisionLoss) {
          DecimalType.adjustPrecisionScale(max(p1 - s1, p2 - s2) + resultScale + 1, resultScale)
        } else {
          DecimalType.bounded(max(p1 - s1, p2 - s2) + resultScale + 1, resultScale)
        }

      case Multiply(e1 @ DecimalType.Expression(p1, s1), e2 @ DecimalType.Expression(p2, s2)) =>
        if (SQLConf.get.decimalOperationsAllowPrecisionLoss) {
          DecimalType.adjustPrecisionScale(p1 + p2 + 1, s1 + s2)
        } else {
          DecimalType.bounded(p1 + p2 + 1, s1 + s2)
        }

      case Divide(e1 @ DecimalType.Expression(p1, s1), e2 @ DecimalType.Expression(p2, s2)) =>
        if (SQLConf.get.decimalOperationsAllowPrecisionLoss) {
          // Precision: p1 - s1 + s2 + max(6, s1 + p2 + 1)
          // Scale: max(6, s1 + p2 + 1)
          val intDig = p1 - s1 + s2
          val scale = max(DecimalType.MINIMUM_ADJUSTED_SCALE, s1 + p2 + 1)
          val prec = intDig + scale
          DecimalType.adjustPrecisionScale(prec, scale)
        } else {
          var intDig = min(DecimalType.MAX_SCALE, p1 - s1 + s2)
          var decDig = min(DecimalType.MAX_SCALE, max(6, s1 + p2 + 1))
          val diff = (intDig + decDig) - DecimalType.MAX_SCALE
          if (diff > 0) {
            decDig -= diff / 2 + 1
            intDig = DecimalType.MAX_SCALE - decDig
          }
          DecimalType.bounded(intDig + decDig, decDig)
        }

      case Remainder(e1 @ DecimalType.Expression(p1, s1), e2 @ DecimalType.Expression(p2, s2)) =>
        if (SQLConf.get.decimalOperationsAllowPrecisionLoss) {
          DecimalType.adjustPrecisionScale(min(p1 - s1, p2 - s2) + max(s1, s2), max(s1, s2))
        } else {
          DecimalType.bounded(min(p1 - s1, p2 - s2) + max(s1, s2), max(s1, s2))
        }

      case Pmod(e1 @ DecimalType.Expression(p1, s1), e2 @ DecimalType.Expression(p2, s2)) =>
        if (SQLConf.get.decimalOperationsAllowPrecisionLoss) {
          DecimalType.adjustPrecisionScale(min(p1 - s1, p2 - s2) + max(s1, s2), max(s1, s2))
        } else {
          DecimalType.bounded(min(p1 - s1, p2 - s2) + max(s1, s2), max(s1, s2))
        }
      case e => e.dataType
    }
  }
}
