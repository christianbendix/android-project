# Shared Expenses App

## Objective

Develop a mobile application (Android) that helps groups of people track and manage their shared expenses, ensuring everyone is aware of their financial obligations with a friendly and intuitive user interface. If you're familiar with the application WeShare or groups in MobilePay, the concept is very much the same.

## Background

In various social and formal gatherings, expenses often occur that are typically shared among participants, such as meals, gifts, or travel costs. Tracking who paid what and calculating how much others owe can become a cumbersome task. A mobile application that alleviates this burden by calculating and notifying members of their respective dues can be incredibly beneficial.

## Functional Requirements

### User Registration & Profile Management

- Allow users to create a profile, including essential information like name and contact details.
- Enable users to update their profiles and manage notification settings.
- You can manage users by creating a custom authentication system, using a third-party provider such as Firebase Authentication or by using dummy data and mocking interactions. (Database and security is not required, but is recommended)

### Group Management

- Allow the creation of groups, adding and removing participants.
- Permit the naming and description of the group.

### Expense Entry & Management

- Enable users to input expenses, specifying the payer and the amount.
- Allow attachment of receipts or invoices (optional feature).
- Facilitate the splitting of expenses among all or selected group members.

### Debt Calculation

- Implement algorithms to calculate how much each person owes or is owed in a group.
- Allow users to view a summary of all transactions, and their current balance (owed/owing) within a group.

### Notifications & Communications

- Notify group members of new expenses and updates.
- Allow users to send reminder notifications to those who owe them money.

## Non-functional Requirements

### Reliability (Quality Attribute, QA)

- **Fault Tolerance**
  - If a request fails due to temporary network unavailability, the application must retry it in the background
  - Failed requests must not prevent the user from continuing to use the app
- **Offline Availability**
  - Previously retrieved data must remain accessible when the device is offline
- **User feedback**
  - The application must indicate whether a request is pending, successful or failed
  - The user must be notified when a previously failed request eventually succeeds in the background

## Use of Generative Artificial Intelligence

GenAI has become a well-established tool for software development and therefore its use is accepted. As engineers we must know when and how to use such tools therefore we expect you to state an example of the following for each of your GenAI use-cases:

- **Use case:** (e.g. UX, code-completion, library integration)
- **Tool:** (e.g. gemini, chatGPT, Claude)
- **Chat history:** (link if possible)
- **Evaluation:** (Description of how well the LLM carried out its task and to what extent you could use its replies)

Look up alternative solutions yourselves (not using GenAI) - why did you end up using what solution?

### AI policy (shared constant)

**Allowed:** any AI tool in any mode, autocomplete, chat, multimodal, or agentic (e.g., Claude Code, Cursor, GitHub Copilot agent mode, Gemini CLI), for any part of this project, provided use is disclosed. Undisclosed AI use is academic misconduct. Note the asymmetry: controlled disclosed AI use that is verified well can earn top marks; hidden use can cost you the course.

Four standing rules apply whenever AI touches your project:

1. **Disclose in the AI dossier.** Continue the log you started in the design document's Appendix A: one entry per significant interaction (tool, mode, task, prompt or link, verification, decision), plus the failure log, at least 3 entries per project documenting AI output that was wrong or off-spec and how you caught it.
2. **AI code enters main only through a pull request** that carries tests, is reviewed by a teammate who did not generate it, and passes CI. 'The model wrote it and it ran' is not verification.
3. **Maintain an agent rules file.** Your repository contains a `CLAUDE.md` or `AGENTS.md` describing your conventions, architecture, and definition of done. Evolve it as you learn what your agents get wrong; its git history is evidence of engineering judgment.
4. **You own what you ship.** At milestone reviews and the final presentation, any team member may be asked to explain (or live-modify) any line in the repository. Inability to do so caps the individual's grade regardless of team output.

## Deliverables

1. **Design document** (3 pages max), following same template as previously, detailing:
   - FR/NFR
   - Navigation model
   - Low-fi wireframe
2. **Implementation document** (4-page max) detailing:
   - Implementation of the application
   - Your report should include an architecture diagram that illustrates how your application supports the Reliability QA, including the main components and their interactions.
   - Your report should also cover the testing of at least one Reliability QA
3. **A short video** demonstrating the application.
4. **Link to group GitHub repository**

## Deadlines

| Milestone | Date |
| --- | --- |
| Submission Deadline (Draft) on Itslearning | November 20th |
| Presentations | November 24th |
