/*
 * SonarQube Java
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.java.checks;

import com.sonar.sslr.api.AstAndTokenVisitor;
import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.Token;
import com.sonar.sslr.squid.checks.SquidCheck;
import org.sonar.check.BelongsToProfile;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.java.ast.api.JavaTokenType;
import org.sonar.java.ast.parser.JavaGrammar;
import org.sonar.sslr.parser.LexerlessGrammar;

@Rule(
  key = "ObjectFinalizeCheck",
  priority = Priority.CRITICAL)
@BelongsToProfile(title = "Sonar way", priority = Priority.CRITICAL)
public class ObjectFinalizeCheck extends SquidCheck<LexerlessGrammar> implements AstAndTokenVisitor {

  enum State {
    EXPECT_FINALIZE,
    EXPECT_LPAREN,
    EXPECT_RPAREN,
    EXPECT_SEMI
  }

  private State state = State.EXPECT_FINALIZE;

  private boolean isInFinalizeMethod;

  @Override
  public void init() {
    subscribeTo(JavaGrammar.MEMBER_DECL);
  }

  @Override
  public void visitFile(AstNode node) {
    isInFinalizeMethod = false;
  }

  @Override
  public void visitNode(AstNode node) {
    if (isFinalizeMethodMember(node)) {
      isInFinalizeMethod = true;
    }
  }

  @Override
  public void leaveNode(AstNode node) {
    if (isFinalizeMethodMember(node)) {
      isInFinalizeMethod = false;
    }
  }

  @Override
  public void visitToken(Token token) {
    switch (state) {
      case EXPECT_FINALIZE:
        state = "finalize".equals(token.getOriginalValue()) ? State.EXPECT_LPAREN : State.EXPECT_FINALIZE;
        break;
      case EXPECT_LPAREN:
        state = "(".equals(token.getOriginalValue()) ? State.EXPECT_RPAREN : State.EXPECT_FINALIZE;
        break;
      case EXPECT_RPAREN:
        state = ")".equals(token.getOriginalValue()) ? State.EXPECT_SEMI : State.EXPECT_FINALIZE;
        break;
      case EXPECT_SEMI:
        if (";".equals(token.getOriginalValue()) && !isInFinalizeMethod) {
          getContext().createLineViolation(this, "Remove this call to finalize().", token);
        }
        state = State.EXPECT_FINALIZE;
        break;
      default:
        throw new IllegalStateException();
    }
  }

  private static boolean isFinalizeMethodMember(AstNode node) {
    return node.hasDirectChildren(JavaGrammar.VOID_METHOD_DECLARATOR_REST) &&
      "finalize".equals(node.getFirstChild(JavaTokenType.IDENTIFIER).getTokenOriginalValue());
  }

}
