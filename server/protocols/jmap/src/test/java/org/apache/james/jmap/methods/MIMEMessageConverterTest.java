/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.methods;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.apache.james.jmap.methods.ValueWithId.MessageWithId;
import org.apache.james.jmap.model.CreationMessage;
import org.apache.james.jmap.model.CreationMessage.DraftEmailer;
import org.apache.james.jmap.model.CreationMessageId;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mime4j.Charsets;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.stream.Field;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class MIMEMessageConverterTest {
    @Test
    public void convertToMimeShouldAddInReplyToHeaderWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        String matchingMessageId = "unique-message-id";
        CreationMessage messageHavingInReplyTo = CreationMessage.builder()
                .from(DraftEmailer.builder().name("sender").build())
                .inReplyToMessageId(matchingMessageId)
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), messageHavingInReplyTo), ImmutableList.of());

        // Then
        assertThat(result.getHeader().getFields("In-Reply-To")).extracting(Field::getBody)
                .containsOnly(matchingMessageId);
    }

    @Test(expected = IllegalArgumentException.class)
    public void convertToMimeShouldThrowWhenMessageIsNull() {
        MIMEMessageConverter sut = new MIMEMessageConverter();

        sut.convertToMime(new ValueWithId.CreationMessageEntry(CreationMessageId.of("any"), null), ImmutableList.of());
    }

    @Test
    public void convertToMimeShouldSetBothFromAndSenderHeaders() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        String joesEmail = "joe@example.com";
        CreationMessage testMessage = CreationMessage.builder()
                .mailboxIds(ImmutableList.of("deadbeef-dead-beef-dead-beef"))
                .subject("subject")
                .from(DraftEmailer.builder().email(joesEmail).name("joe").build())
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getFrom()).extracting(Mailbox::getAddress).allMatch(f -> f.equals(joesEmail));
        assertThat(result.getSender().getAddress()).isEqualTo(joesEmail);
    }

    @Test
    public void convertToMimeShouldSetCorrectLocalDate() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        Instant now = Instant.now();
        ZonedDateTime messageDate = ZonedDateTime.ofInstant(now, ZoneId.systemDefault());

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .date(messageDate)
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getDate()).isEqualToIgnoringMillis(Date.from(now));
    }

    @Test
    public void convertToMimeShouldSetQuotedPrintableContentTransferEncodingWhenText() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all</b>!")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getHeader()
                .getField("Content-Transfer-Encoding")
                .getBody())
            .isEqualTo("quoted-printable");
    }

    @Test
    public void convertToMimeShouldSetTextBodyWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();
        TextBody expected = new BasicBodyFactory().textBody("Hello all!", Charsets.UTF_8);

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .textBody("Hello all!")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getBody()).isEqualToComparingOnlyGivenFields(expected, "content", "charset");
    }

    @Test
    public void convertToMimeShouldSetEmptyBodyWhenNoBodyProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();
        TextBody expected = new BasicBodyFactory().textBody("", Charsets.UTF_8);

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getBody()).isEqualToComparingOnlyGivenFields(expected, "content", "charset");
    }

    @Test
    public void convertToMimeShouldSetHtmlBodyWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();
        TextBody expected = new BasicBodyFactory().textBody("Hello <b>all</b>!", Charsets.UTF_8);

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all</b>!")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getBody()).isEqualToComparingOnlyGivenFields(expected, "content", "charset");
    }

    @Test
    public void convertToMimeShouldGenerateMultipartWhenHtmlBodyAndTextBodyProvided() throws Exception {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .textBody("Hello all!")
                .htmlBody("Hello <b>all</b>!")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getBody()).isInstanceOf(Multipart.class);
        assertThat(result.isMultipart()).isTrue();
        assertThat(result.getMimeType()).isEqualTo("multipart/alternative");
        Multipart typedResult = (Multipart)result.getBody();
        assertThat(typedResult.getBodyParts()).hasSize(2);
    }

    @Test
    public void convertShouldGenerateExpectedMultipartWhenHtmlAndTextBodyProvided() throws Exception {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .textBody("Hello all!")
                .htmlBody("Hello <b>all</b>!")
                .build();

        String expectedHeaders = "MIME-Version: 1.0\r\n" +
                "Content-Type: multipart/alternative;\r\n" +
                " boundary=\"-=Part.1.";
        String expectedPart1 = "Content-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: quoted-printable\r\n" +
                "\r\n" +
                "Hello all!\r\n";
        String expectedPart2 = "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: quoted-printable\r\n" +
                "\r\n" +
                "Hello <b>all</b>!\r\n";

        // When
        byte[] convert = sut.convert(new MessageWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        String actual = new String(convert, Charsets.UTF_8);
        assertThat(actual).startsWith(expectedHeaders);
        assertThat(actual).contains(expectedPart1);
        assertThat(actual).contains(expectedPart2);
    }

    @Test
    public void convertToMimeShouldSetMimeTypeWhenTextBody() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .textBody("Hello all!")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getMimeType()).isEqualTo("text/plain");
    }

    @Test
    public void convertToMimeShouldSetMimeTypeWhenHtmlBody() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getMimeType()).isEqualTo("text/html");
    }

    @Test
    public void convertToMimeShouldSetEmptyHtmlBodyWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();
        TextBody expected = new BasicBodyFactory().textBody("", Charsets.UTF_8);

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getBody()).isEqualToComparingOnlyGivenFields(expected, "content", "charset");
        assertThat(result.getMimeType()).isEqualTo("text/html");
    }

    @Test
    public void convertToMimeShouldSetEmptyTextBodyWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();
        TextBody expected = new BasicBodyFactory().textBody("", Charsets.UTF_8);

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .textBody("")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getBody()).isEqualToComparingOnlyGivenFields(expected, "content", "charset");
        assertThat(result.getMimeType()).isEqualTo("text/plain");
    }

    @Test
    public void convertToMimeShouldAddAttachmentWhenOne() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

        String expectedCID = "cid";
        String expectedMimeType = "image/png";
        String text = "123456";
        TextBody expectedBody = new BasicBodyFactory().textBody(text.getBytes(), Charsets.UTF_8);
        MessageAttachment attachment = MessageAttachment.builder()
                .attachment(org.apache.james.mailbox.model.Attachment.builder()
                    .attachmentId(AttachmentId.from("blodId"))
                    .bytes(text.getBytes())
                    .type(expectedMimeType)
                    .build())
                .cid(Cid.from(expectedCID))
                .isInline(true)
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(attachment));

        // Then
        assertThat(result.getBody()).isInstanceOf(Multipart.class);
        assertThat(result.isMultipart()).isTrue();
        Multipart typedResult = (Multipart)result.getBody();
        assertThat(typedResult.getBodyParts()).hasSize(2);
        Entity attachmentPart = typedResult.getBodyParts().get(1);
        assertThat(attachmentPart.getBody()).isEqualToComparingOnlyGivenFields(expectedBody, "content");
        assertThat(attachmentPart.getDispositionType()).isEqualTo("inline");
        assertThat(attachmentPart.getMimeType()).isEqualTo(expectedMimeType);
        assertThat(attachmentPart.getHeader().getField("Content-ID").getBody()).isEqualTo(expectedCID);
        assertThat(attachmentPart.getContentTransferEncoding()).isEqualTo("base64");
    }

    @Test
    public void convertToMimeShouldAddAttachmentAndMultipartAlternativeWhenOneAttachementAndTextAndHtmlBody() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .textBody("Hello all!")
                .htmlBody("Hello <b>all<b>!")
                .build();
        TextBody expectedTextBody = new BasicBodyFactory().textBody("Hello all!".getBytes(), Charsets.UTF_8);
        TextBody expectedHtmlBody = new BasicBodyFactory().textBody("Hello <b>all<b>!".getBytes(), Charsets.UTF_8);

        String expectedCID = "cid";
        String expectedMimeType = "image/png";
        String text = "123456";
        TextBody expectedAttachmentBody = new BasicBodyFactory().textBody(text.getBytes(), Charsets.UTF_8);
        MessageAttachment attachment = MessageAttachment.builder()
                .attachment(org.apache.james.mailbox.model.Attachment.builder()
                    .attachmentId(AttachmentId.from("blodId"))
                    .bytes(text.getBytes())
                    .type(expectedMimeType)
                    .build())
                .cid(Cid.from(expectedCID))
                .isInline(true)
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(attachment));

        // Then
        assertThat(result.getBody()).isInstanceOf(Multipart.class);
        assertThat(result.isMultipart()).isTrue();
        Multipart typedResult = (Multipart)result.getBody();
        assertThat(typedResult.getBodyParts()).hasSize(2);
        Entity mainBodyPart = typedResult.getBodyParts().get(0);
        assertThat(mainBodyPart.getBody()).isInstanceOf(Multipart.class);
        assertThat(mainBodyPart.isMultipart()).isTrue();
        assertThat(mainBodyPart.getMimeType()).isEqualTo("multipart/alternative");
        assertThat(((Multipart)mainBodyPart.getBody()).getBodyParts()).hasSize(2);
        Entity textPart = ((Multipart)mainBodyPart.getBody()).getBodyParts().get(0);
        Entity htmlPart = ((Multipart)mainBodyPart.getBody()).getBodyParts().get(1);
        assertThat(textPart.getBody()).isEqualToComparingOnlyGivenFields(expectedTextBody, "content");
        assertThat(htmlPart.getBody()).isEqualToComparingOnlyGivenFields(expectedHtmlBody, "content");

        Entity attachmentPart = typedResult.getBodyParts().get(1);
        assertThat(attachmentPart.getBody()).isEqualToComparingOnlyGivenFields(expectedAttachmentBody, "content");
        assertThat(attachmentPart.getDispositionType()).isEqualTo("inline");
        assertThat(attachmentPart.getMimeType()).isEqualTo(expectedMimeType);
        assertThat(attachmentPart.getHeader().getField("Content-ID").getBody()).isEqualTo(expectedCID);
    }

    @Test
    public void convertShouldEncodeWhenNonASCIICharacters() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Some non-ASCII characters: áÄÎßÿ")
                .build();

        // When
        ImmutableList<MessageAttachment> attachments = ImmutableList.of();
        byte[] convert = sut.convert(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), attachments);

        String expectedEncodedContent = "Some non-ASCII characters: =C3=A1=C3=84=C3=8E=C3=9F=C3=BF";

        // Then
        String actual = new String(convert, Charsets.US_ASCII);
        assertThat(actual).contains(expectedEncodedContent);
    }
}