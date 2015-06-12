package com.faunadb.client.java.query;

import com.fasterxml.jackson.annotation.JsonProperty;

public class If implements Expression {
  public static If create(Expression condition, Expression then, Expression elseExpression) {
    return new If(condition, then, elseExpression);
  }

  @JsonProperty("if")
  private final Expression condition;
  @JsonProperty("then")
  private final Expression then;
  @JsonProperty("else")
  private final Expression elseExpression;

  If(Expression condition, Expression then, Expression elseExpression) {
    this.condition = condition;
    this.then = then;
    this.elseExpression = elseExpression;
  }

  public Expression condition() {
    return condition;
  }

  public Expression then() {
    return then;
  }

  public Expression elseExpression() {
    return elseExpression;
  }
}