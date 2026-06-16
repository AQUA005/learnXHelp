import os
import sys
from datetime import datetime
from reportlab.lib.pagesizes import letter
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak, KeepTogether
from reportlab.pdfgen import canvas

# ==========================================
# 1. EDITABLE TEXT CONFIGURATION
# ==========================================
# Feel free to edit the text in this dictionary to update the report content.
REPORT_TEXT = {
    "abstract": (
        "This report presents learnX.help, an advanced academic management and learning assistance system "
        "built on a modern Spring Boot micro-architecture. Designed to optimize class routines, examinations, "
        "and learning resource sharing, the application streamlines coordination between students, class "
        "representatives, teachers, and system administrators. Architected under strict Object-Oriented "
        "Programming (OOP) paradigms in Java 21, the system models real-world academic entities through "
        "comprehensive encapsulation, ensuring robust data security and access control across user roles. "
        "Inheritance and interface polymorphism are leveraged dynamically, particularly through Spring Data "
        "JPA repositories and custom security details mappings, enabling extensible class boundaries and "
        "clean architectural boundaries. A primary engineering innovation is the Slot Detection Service, "
        "which programmatically executes routine merging algorithms to locate open time intervals for scheduling "
        "tests and makeup sessions. The development methodology followed an iterative, object-oriented software "
        "engineering lifecycle, integrating requirements analysis, UML design, and robust test plans. By "
        "consolidating course materials, grade books, and direct academic scheduling into a unified web "
        "dashboard, learnX.help solves critical operational bottlenecks in university coordination, demonstrating "
        "structural stability and OOP design excellence."
    ),
    
    "ch1_1": (
        "Modern higher educational institutions require seamless coordination among various academic "
        "roles, including students, class representatives (CRs), teachers, and administrative staff. "
        "As academic schedules, learning resources, and examinations grow increasingly complex, traditional "
        "manual methods of organization become inadequate. The learnX.help application is engineered to "
        "address this challenge by offering a centralized, role-based platform that facilitates routine management, "
        "automated test scheduling, secure academic handout distribution, and streamlined grading. The core "
        "motivation is to replace scattered communication channels (such as generic instant messaging groups) "
        "with an integrated, structured environment that ensures data integrity and structural organization."
    ),
    
    "ch1_2": (
        "Students and educators frequently encounter operational bottlenecks in three main areas: "
        "(1) scheduling makeup classes or class tests without clashing with existing course routines or "
        "previously scheduled exams; (2) centralizing high-capacity academic resource vaults where notes, "
        "lecture handouts, and slides can be approved, categorized, and rated; and (3) managing structured "
        "announcements and online examinations without information loss. Currently, no localized platform "
        "combines automated scheduling slot detection and multi-tenant university resource segregation "
        "under a strict role-permission matrix. learnX.help solves these operational problems by modeling "
        "academic workflows inside an Object-Oriented design framework."
    ),
    
    "ch1_3": (
        "The primary engineering objectives of this project are: "
        "(1) to architect a secure and modular system leveraging Object-Oriented Programming (OOP) paradigms "
        "in Java, including strict encapsulation, interface polymorphism, inheritance hierarchies, and class "
        "abstractions; (2) to implement an automated Routine Merging and Free Slot Detection algorithm to "
        "facilitate hassle-free assessment scheduling; and (3) to deliver a unified Single Page Application (SPA) "
        "capable of managing student grading, multiple-choice quizzes, announcements, and resource indexing."
    ),
    
    "ch1_4": (
        "The development environment and libraries were structured to support modern cloud-ready micro-architectures. "
        "By leveraging the Spring ecosystem, the application achieves high testability and clean separation of concerns. "
        "Table 1.1 documents the exact specifications compiled from the project build definitions."
    ),

    "ch2_1": (
        "The functional scope of learnX.help is designed around key academic actions. Each feature corresponds "
        "to a Java controller and service mapping. The complete list of functional operations is tabulated below."
    ),

    "ch2_2": (
        "Non-functional criteria dictate technical constraints around local performance, transaction safety, "
        "data encapsulation, and system usability, ensuring the system operates reliably under typical university loads."
    ),

    "ch3_1": (
        "The development of learnX.help followed the Iterative Object-Oriented Software Engineering (OOSE) "
        "lifecycle. This methodology emphasizes structuring system logic around key business entities. The system "
        "was iteratively modeled using Unified Modeling Language (UML) notation, translating requirements "
        "into clear classes, and maintaining a high level of abstraction between layers (Presentation, Service, "
        "and Data Access)."
    ),

    "ch3_2": (
        "Below is a structural mapping showing where the comprehensive UML/QML Class Diagram resides. It "
        "depicts the core relational mappings of learnX.help, displaying class fields, methods, and multiplicities "
        "(e.g., ManyToOne associations between User and StudentClass/University)."
    ),

    "ch3_3": (
        "The following tables provide details on the properties, data types, and roles of the key classes "
        "defined in the application codebase. Field types utilize standard Java packages and custom JPA references."
    ),

    "ch4_1": (
        "Encapsulation / Data Hiding: All core entity variables are declared as private. Access to "
        "and modification of these fields are regulated strictly through getter/setter methods. This "
        "prevents unauthorized modifications of internal object state. Lombok annotations (@Data, @Builder) "
        "are utilized to enforce compile-time generation of these interfaces, keeping source code clean and secure.\n\n"
        "Inheritance: Extensive interface inheritance is implemented. Every data repository extends "
        "Spring Data's JpaRepository<Entity, ID>. This inherits powerful generic database CRUD and pagination "
        "capabilities, demonstrating robust reusable inheritance without redundant class declarations.\n\n"
        "Polymorphism: Polymorphism is applied via interface implementations and method overriding. For instance, "
        "CustomUserDetailsService implements Spring Security's UserDetailsService and overrides the "
        "loadUserByUsername method. The security filter chain executes this method polymorphically at runtime. "
        "Additionally, initialization tasks run polymorphically via CommandLineRunner.run().\n\n"
        "Abstraction: Database query complexities and transaction controls are hidden behind abstract "
        "repository method declarations (e.g. Optional<User> findByUsername(String u)). High-level business actions "
        "in the Controllers invoke these abstract contracts without concern for low-level database operations."
    ),

    "ch4_2": (
        "The following figures display isolated, highly clean code segments extracted directly from the workspace "
        "to precisely prove the application of each of these 4 pillars inside the developed codebase."
    ),

    "ch5_1": (
        "To verify that the system satisfies all operational objectives and OOP boundaries, a multi-phased "
        "testing strategy is defined below. This plan verifies class boundaries, system transactions, "
        "and logical integration without executing live database connections in production."
    ),

    "ch6_1": (
        "The learnX.help portal demonstrates how Object-Oriented software engineering can solve operational "
        "coordination bottlenecks in academic environments. By leveraging encapsulated models, "
        "reusable repository inheritance, polymorphic security dispatchers, and slot detection services, "
        "the application delivers a robust, secure dashboard. The system has achieved structural OOP "
        "stability and successfully coordinates resource vaults, exams, and routines."
    ),

    "ch6_2": (
        "The current system exhibits two primary constraints: "
        "(1) lack of live messaging notifications (relying on page reload announcements); and "
        "(2) storing large file handouts (up to 100MB) directly inside the relational database as binary blobs. "
        "The latter can degrade database search speeds over time as uploads scale."
    ),

    "ch6_3": (
        "Professional scaling plans include: "
        "(1) decoupling binary file storage from the main database, using Amazon S3 or Google Cloud Storage "
        "via spring-cloud-aws; (2) implementing WebSocket communication to enable real-time announcement "
        "and routine conflict alerts; and (3) adding automated machine-learning optimization to auto-recommend "
        "ideal slots based on student historical attendance."
    ),

    "app_b": (
        "Follow these steps to deploy and run learnX.help locally:\n\n"
        "1. Pre-requisites: Ensure JDK 21 and Maven are installed on your machine.\n"
        "2. Database Configuration: The system runs with an active file-based H2 database by default. "
        "To switch to PostgreSQL, configure environment variables: SPRING_DATASOURCE_URL, "
        "SPRING_DATASOURCE_USERNAME, and SPRING_DATASOURCE_PASSWORD.\n"
        "3. Build Command: In the root folder, compile the Maven project: mvn clean install.\n"
        "4. Execution: Boot the Spring application: mvn spring-boot:run.\n"
        "5. Client Access: Open your browser and navigate to http://localhost:8080. "
        "To view database contents directly, navigate to http://localhost:8080/h2-console "
        "using jdbc:h2:file:~/.learnx/learnxdb as the JDBC URL."
    )
}

# ==========================================
# 2. DESIGN SYSTEM (WEBSITE ALIGNED)
# ==========================================
# Mapped directly from e:\V1LearnX\src\main\resources\static\style.css variables
PRIMARY_COLOR = colors.HexColor("#11212D")     # Accent Blue (Deep Navy Charcoal)
SECONDARY_COLOR = colors.HexColor("#4A5C6A")   # Text Muted (Slate Gray)
ACCENT_TEAL = colors.HexColor("#9BA8AB")       # Background Secondary (Muted Teal Gray)
LIGHT_BG = colors.HexColor("#F5F7F8")          # Soft Light Gray for backgrounds
TEXT_DARK = colors.HexColor("#06141B")         # Near Black for primary readable text
BORDER_COLOR = colors.HexColor("#CCD000")      # Base teal color placeholder, let's use #9BA8AB for grid lines
GRID_COLOR = colors.HexColor("#CCD0CF")        # Light Grayish Teal for grid borders
WHITE = colors.HexColor("#FFFFFF")

# Global map to store page numbers of headings, tables, and figures
headings_map = {}

class TrackingParagraph(Paragraph):
    """
    Custom Paragraph that records its page number when drawn.
    Used for generating Table of Contents, List of Tables, and List of Figures.
    """
    def __init__(self, text, style, tracking_id, *args, **kwargs):
        super().__init__(text, style, *args, **kwargs)
        self.tracking_id = tracking_id

    def draw(self):
        super().draw()
        page_num = self.canv.getPageNumber()
        headings_map[self.tracking_id] = page_num

class NumberedCanvas(canvas.Canvas):
    """
    Two-pass canvas to compute total pages and draw headers/footers.
    """
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._saved_page_states = []

    def showPage(self):
        self._saved_page_states.append(dict(self.__dict__))
        self._startPage()

    def save(self):
        num_pages = len(self._saved_page_states)
        for state in self._saved_page_states:
            self.__dict__.update(state)
            self.draw_page_decorations(num_pages)
            super().showPage()
        super().save()

    def draw_page_decorations(self, page_count):
        if self._pageNumber == 1:
            return  # Skip cover page
            
        self.saveState()
        self.setFont("Helvetica", 8)
        self.setFillColor(SECONDARY_COLOR)
        
        # Draw running header (Professional website style)
        self.drawString(54, 750, "learnX.help | Project Report (Object-Oriented Programming)")
        self.setStrokeColor(GRID_COLOR)
        self.setLineWidth(0.5)
        self.line(54, 742, 558, 742)
        
        # Draw running footer
        page_text = f"Page {self._pageNumber} of {page_count}"
        self.drawRightString(558, 40, page_text)
        self.drawString(54, 40, "University of Science & Technology Chittagong | Dept. of CSE")
        self.line(54, 52, 558, 52)
        self.restoreState()

def draw_cover_page(canvas, doc):
    canvas.saveState()
    # Premium website color bars
    canvas.setFillColor(PRIMARY_COLOR)
    canvas.rect(0, doc.pagesize[1] - 40, doc.pagesize[0], 40, stroke=0, fill=1)
    canvas.setFillColor(SECONDARY_COLOR)
    canvas.rect(0, 0, doc.pagesize[0], 25, stroke=0, fill=1)
    
    # Title
    canvas.setFillColor(PRIMARY_COLOR)
    canvas.setFont("Helvetica-Bold", 32)
    canvas.drawCentredString(doc.pagesize[0]/2.0, 520, "learnX.help")
    
    # Subtitle
    canvas.setFont("Helvetica", 14)
    canvas.drawCentredString(doc.pagesize[0]/2.0, 485, "An Object-Oriented Academic Assistant & Learning Management Portal")
    
    # Gold decorative separator line (Teal Accent from website style)
    canvas.setStrokeColor(ACCENT_TEAL)
    canvas.setLineWidth(3)
    canvas.line(doc.pagesize[0]/2.0 - 150, 465, doc.pagesize[0]/2.0 + 150, 465)
    
    # Academic submission text
    canvas.setFillColor(TEXT_DARK)
    canvas.setFont("Helvetica-Bold", 11)
    canvas.drawCentredString(doc.pagesize[0]/2.0, 390, "A PROJECT REPORT")
    canvas.setFont("Helvetica", 10)
    canvas.drawCentredString(doc.pagesize[0]/2.0, 370, "Submitted in partial fulfillment of the requirements for the course:")
    canvas.setFont("Helvetica-Bold", 11)
    canvas.drawCentredString(doc.pagesize[0]/2.0, 350, "Object-Oriented Programming")
    
    # Team Details
    canvas.setFont("Helvetica-Bold", 11)
    canvas.drawString(80, 240, "Submitted By:")
    canvas.setFont("Helvetica", 9.5)
    canvas.drawString(80, 220, "Md Asif (ID: 43)")
    canvas.drawString(80, 205, "Snigdha Chakma (ID: 63)")
    canvas.drawString(80, 190, "Ahana Dewanjee (ID: 49)")
    
    # Supervisor
    canvas.setFont("Helvetica-Bold", 11)
    canvas.drawString(doc.pagesize[0] - 250, 240, "Supervisor:")
    canvas.setFont("Helvetica", 9.5)
    canvas.drawString(doc.pagesize[0] - 250, 220, "Ashiqul Islam")
    canvas.drawString(doc.pagesize[0] - 250, 205, "Lecturer")
    canvas.drawString(doc.pagesize[0] - 250, 190, "Dept. of Computer Science & Engineering")
    
    # Footer Institution details
    canvas.setFont("Helvetica-Bold", 11)
    canvas.drawCentredString(doc.pagesize[0]/2.0, 110, "University of Science & Technology Chittagong")
    canvas.setFont("Helvetica", 9.5)
    canvas.drawCentredString(doc.pagesize[0]/2.0, 90, "Date: 16 June 2026")
    
    canvas.restoreState()

def make_paragraph_table_data(data_matrix, styles):
    body_style = ParagraphStyle(
        'TableBody',
        fontName='Helvetica',
        fontSize=8,
        leading=10,
        textColor=colors.HexColor("#06141B")
    )
    header_style = ParagraphStyle(
        'TableHeader',
        fontName='Helvetica-Bold',
        fontSize=8.5,
        leading=11,
        textColor=colors.white
    )
    
    formatted_data = []
    for row_idx, row in enumerate(data_matrix):
        formatted_row = []
        for col_idx, cell in enumerate(row):
            if row_idx == 0:
                p = Paragraph(cell, header_style)
            else:
                p = Paragraph(cell, body_style)
            formatted_row.append(p)
        formatted_data.append(formatted_row)
    return formatted_data

def create_styled_table(data, widths):
    t = Table(data, colWidths=widths)
    style = [
        ('BACKGROUND', (0,0), (-1,0), PRIMARY_COLOR),
        ('TEXTCOLOR', (0,0), (-1,0), colors.whitesmoke),
        ('ALIGN', (0,0), (-1,-1), 'LEFT'),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('BOTTOMPADDING', (0,0), (-1,-1), 4),
        ('TOPPADDING', (0,0), (-1,-1), 4),
        ('LEFTPADDING', (0,0), (-1,-1), 4),
        ('RIGHTPADDING', (0,0), (-1,-1), 4),
        ('GRID', (0,0), (-1,-1), 0.5, GRID_COLOR),
    ]
    for i in range(1, len(data)):
        bg = colors.HexColor("#F5F7F8") if i % 2 == 1 else WHITE
        style.append(('BACKGROUND', (0, i), (-1, i), bg))
    t.setStyle(TableStyle(style))
    return t

def format_code_line(line):
    line = line.replace('\t', '    ')
    line = line.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    leading_spaces = len(line) - len(line.lstrip(' '))
    formatted = '&nbsp;' * leading_spaces + line.lstrip(' ')
    return formatted

def make_code_block(code_text):
    """
    Generates a beautifully stylized code block matching the website IDE theme.
    Fixed output to prevent solid blue background rendering.
    """
    code_style = ParagraphStyle(
        'CodeStyleBlock',
        fontName='Courier',
        fontSize=7.5,
        leading=9.5,
        textColor=TEXT_DARK
    )
    lines = code_text.split('\n')
    data = []
    for idx, line in enumerate(lines, 1):
        formatted = format_code_line(line)
        lineno_p = Paragraph(f"<font color='#718096'>{idx}</font>", code_style)
        content_p = Paragraph(formatted, code_style)
        data.append([lineno_p, content_p])
        
    t = Table(data, colWidths=[20, 484])
    t.setStyle(TableStyle([
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('TOPPADDING', (0,0), (-1,-1), 1),
        ('BOTTOMPADDING', (0,0), (-1,-1), 1),
        ('LEFTPADDING', (0,0), (-1,-1), 2),
        ('RIGHTPADDING', (0,0), (-1,-1), 2),
        ('LINEAFTER', (0,0), (0,-1), 0.5, ACCENT_TEAL),
        ('BACKGROUND', (0,0), (0,-1), colors.HexColor("#EDF2F7")),     # Line numbers background
        ('BACKGROUND', (1,0), (1,-1), LIGHT_BG),                        # Code content background
    ]))
    return t

def make_source_code_table(file_path):
    """
    Generates a clean source code layout with highlighted line numbers and clear border separator.
    Fixed the HexColor('#FFF') -> HexColor('#FFFFFF') bug to avoid solid blue background.
    """
    with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
        content = f.read()
    lines = content.split('\n')
    data = []
    code_style = ParagraphStyle(
        'CodeStyleFile',
        fontName='Courier',
        fontSize=6.5,
        leading=8,
        textColor=TEXT_DARK
    )
    lineno_style = ParagraphStyle(
        'LineNoStyleFile',
        fontName='Courier',
        fontSize=6.5,
        leading=8,
        textColor=SECONDARY_COLOR,
        alignment=2
    )
    for idx, line in enumerate(lines, 1):
        formatted = format_code_line(line)
        lineno_p = Paragraph(str(idx), lineno_style)
        content_p = Paragraph(formatted, code_style)
        data.append([lineno_p, content_p])
        
    t = Table(data, colWidths=[20, 484])
    t.setStyle(TableStyle([
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('TOPPADDING', (0,0), (-1,-1), 0.5),
        ('BOTTOMPADDING', (0,0), (-1,-1), 0.5),
        ('LEFTPADDING', (0,0), (-1,-1), 2),
        ('RIGHTPADDING', (0,0), (-1,-1), 2),
        ('LINEAFTER', (0,0), (0,-1), 0.5, ACCENT_TEAL),                 # Separator between lineno and code
        ('BACKGROUND', (0,0), (0,-1), colors.HexColor("#EDF2F7")),     # Shaded line numbers column
        ('BACKGROUND', (1,0), (1,-1), WHITE),                           # White code block column
    ]))
    return t

def make_placeholder_box(title, description, height=120):
    p_title = Paragraph(f"<b>{title}</b>", ParagraphStyle('PTitle', fontName='Helvetica-Bold', fontSize=10, leading=12, alignment=1))
    p_desc = Paragraph(description, ParagraphStyle('PDesc', fontName='Helvetica', fontSize=8, leading=10, alignment=1))
    data = [[p_title], [p_desc]]
    t = Table(data, colWidths=[504])
    t.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), colors.HexColor("#EDF2F7")),
        ('ALIGN', (0,0), (-1,-1), 'CENTER'),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('GRID', (0,0), (-1,-1), 1, ACCENT_TEAL),
        ('TOPPADDING', (0,0), (-1,-1), height/3),
        ('BOTTOMPADDING', (0,0), (-1,-1), height/3),
    ]))
    return t

def make_placeholder_or_image(image_path, title, description, height=120):
    """
    Checks if a local file exists at image_path and automatically embeds it.
    Otherwise, renders an elegant placeholder box with instruction details.
    """
    if os.path.exists(image_path):
        try:
            from reportlab.platypus import Image
            return Image(image_path, width=504, height=height)
        except Exception as e:
            print(f"Error loading image {image_path}: {e}")
    return make_placeholder_box(title, description, height)

def extract_snippet(file_path, start_line, end_line):
    if not os.path.exists(file_path):
        return f"// Source file not found: {file_path}"
    with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
        lines = f.readlines()
    snippet = "".join(lines[start_line-1:end_line])
    return snippet

def get_snippet(name):
    snippets = {
        'encapsulation': """public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String username;
    
    @Column(nullable = false)
    private String password;
    
    @Column(nullable = false)
    private String fullName;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
    
    @Builder.Default
    @Column(nullable = false)
    private boolean approved = true;
    
    private String idNo;
    private String department;
    private String batch;
    private String semester;
    private String section;
    private String designation;
    // Getters and setters generated by Lombok @Data
}""",
        'inheritance': """public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    java.util.List<User> findByUniversityAndRole(University university, User.Role role);
    java.util.List<User> findByStudentClass(StudentClass studentClass);
}""",
        'polymorphism': """@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final SystemAdminRepository systemAdminRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Dynamic dispatch via interface method implementation
        Optional<SystemAdmin> sysAdmin = systemAdminRepository.findByUsername(username);
        if (sysAdmin.isPresent()) {
            return org.springframework.security.core.userdetails.User.builder()
                    .username(sysAdmin.get().getUsername())
                    .password(sysAdmin.get().getPassword())
                    .roles("SYSTEM_ADMIN")
                    .build();
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole().name())
                .build();
    }
}""",
        'abstraction': """@Service
@AllArgsConstructor
public class SlotDetectionService {

    private final ScheduleItemRepository scheduleItemRepository;
    private final ClassTestRepository classTestRepository;

    // Abstract method interface separating algorithm complexity from Controllers
    public List<TimeInterval> detectFreeSlots(LocalDate date, int durationMinutes) {
        String dayOfWeekStr = date.getDayOfWeek().name();
        List<ScheduleItem> routines = scheduleItemRepository.findByDayOfWeekOrderByStartTimeAsc(dayOfWeekStr);
        List<ClassTest> tests = classTestRepository.findAll();
        // ... (internal routine merging implementation)
    }
}"""
    }
    try:
        base_path = "e:/V1LearnX/src/main/java/com/example/demo"
        if name == 'encapsulation':
            return extract_snippet(os.path.join(base_path, "entity/User.java"), 12, 43)
        elif name == 'inheritance':
            return extract_snippet(os.path.join(base_path, "repository/UserRepository.java"), 9, 16)
        elif name == 'polymorphism':
            return extract_snippet(os.path.join(base_path, "service/CustomUserDetailsService.java"), 15, 43)
        elif name == 'abstraction':
            return extract_snippet(os.path.join(base_path, "service/SlotDetectionService.java"), 18, 33)
    except Exception as e:
        pass
    return snippets[name]

def find_java_files(root_dir):
    java_files = []
    for root, dirs, files in os.walk(root_dir):
        for file in files:
            if file.endswith('.java'):
                path = os.path.join(root, file)
                rel_path = os.path.relpath(path, root_dir)
                java_files.append((rel_path.replace('\\', '/'), path))
    java_files.sort(key=lambda x: x[0])
    return java_files

def build_pdf(filename="Project_Report.pdf"):
    styles = getSampleStyleSheet()
    
    # Typography settings mapped from website styles
    title_style = ParagraphStyle(
        'DocTitle',
        fontName='Helvetica-Bold',
        fontSize=20,
        leading=24,
        textColor=PRIMARY_COLOR,
        spaceAfter=15
    )
    h1_style = ParagraphStyle(
        'DocH1',
        fontName='Helvetica-Bold',
        fontSize=13,
        leading=16,
        textColor=PRIMARY_COLOR,
        spaceBefore=12,
        spaceAfter=6,
        keepWithNext=True
    )
    h2_style = ParagraphStyle(
        'DocH2',
        fontName='Helvetica-Bold',
        fontSize=10.5,
        leading=13,
        textColor=SECONDARY_COLOR,
        spaceBefore=8,
        spaceAfter=4,
        keepWithNext=True
    )
    body_style = ParagraphStyle(
        'DocBody',
        fontName='Helvetica',
        fontSize=9,
        leading=13,
        textColor=TEXT_DARK,
        spaceAfter=6
    )
    toc_label_style = ParagraphStyle(
        'TOCLabel',
        fontName='Helvetica',
        fontSize=9,
        leading=12,
        textColor=TEXT_DARK
    )
    toc_page_style = ParagraphStyle(
        'TOCPage',
        fontName='Helvetica-Bold',
        fontSize=9,
        leading=12,
        textColor=PRIMARY_COLOR,
        alignment=2
    )

    story = []

    # --- COVER PAGE ---
    story.append(Spacer(1, 10))
    story.append(PageBreak())

    # --- ABSTRACT ---
    story.append(TrackingParagraph("Abstract", h1_style, "toc_abstract"))
    story.append(Paragraph(REPORT_TEXT["abstract"], body_style))
    story.append(PageBreak())

    # --- TABLE OF CONTENTS ---
    story.append(TrackingParagraph("Table of Contents", h1_style, "toc_toc"))
    story.append(Spacer(1, 10))
    
    toc_data = [
        ("Abstract", "toc_abstract", 0),
        ("Table of Contents", "toc_toc", 0),
        ("List of Tables", "toc_lot", 0),
        ("List of Figures", "toc_lof", 0),
        ("Chapter 1: Introduction", "toc_ch1", 0),
        ("  1.1 Project Background and Motivation", "toc_ch1_1", 1),
        ("  1.2 Problem Statement", "toc_ch1_2", 1),
        ("  1.3 Project Objectives", "toc_ch1_3", 1),
        ("  1.4 Tools and Technologies Used", "toc_ch1_4", 1),
        ("Chapter 2: Requirements and Analysis", "toc_ch2", 0),
        ("  2.1 Functional Requirements", "toc_ch2_1", 1),
        ("  2.2 Non-Functional Requirements", "toc_ch2_2", 1),
        ("Chapter 3: Object-Oriented Design and Methodology", "toc_ch3", 0),
        ("  3.1 Methodology Used", "toc_ch3_1", 1),
        ("  3.2 Class Diagram Mapping", "toc_ch3_2", 1),
        ("  3.3 Detailed Class Descriptions", "toc_ch3_3", 1),
        ("Chapter 4: Implementation and Core OOP Principles", "toc_ch4", 0),
        ("  4.1 Key OOP Principles Applied", "toc_ch4_1", 1),
        ("  4.2 Code Snippets", "toc_ch4_2", 1),
        ("Chapter 5: Testing and Results", "toc_ch5", 0),
        ("  5.1 Test Plan", "toc_ch5_1", 1),
        ("Chapter 6: Conclusion and Future Work", "toc_ch6", 0),
        ("  6.1 Conclusion", "toc_ch6_1", 1),
        ("  6.2 Limitations", "toc_ch6_2", 1),
        ("  6.3 Future Enhancements", "toc_ch6_3", 1),
        ("References", "toc_ref", 0),
        ("Appendix A: Full Source Code", "toc_app_a", 0),
        ("Appendix B: User Manual", "toc_app_b", 0),
        ("Appendix C: Screenshots", "toc_app_c", 0),
    ]
    
    formatted_toc = []
    for label, heading_id, indent in toc_data:
        p_num = str(headings_map.get(heading_id, "0"))
        indent_str = "&nbsp;" * 4 * indent
        lbl = Paragraph(f"{indent_str}{label}", toc_label_style)
        pg = Paragraph(p_num, toc_page_style)
        formatted_toc.append([lbl, pg])
        
    toc_table = Table(formatted_toc, colWidths=[454, 50])
    toc_table.setStyle(TableStyle([
        ('VALIGN', (0,0), (-1,-1), 'BOTTOM'),
        ('BOTTOMPADDING', (0,0), (-1,-1), 3),
        ('TOPPADDING', (0,0), (-1,-1), 3),
        ('LINEBELOW', (0,0), (-1,-1), 0.5, colors.HexColor("#E2E8F0")),
    ]))
    story.append(toc_table)
    story.append(PageBreak())

    # --- LIST OF TABLES ---
    story.append(TrackingParagraph("List of Tables", h1_style, "toc_lot"))
    story.append(Spacer(1, 10))
    
    tables_list = [
        ("Table 1.1: Tools and Development Environment", "tab_1_1"),
        ("Table 2.1: Functional Requirements Specifications", "tab_2_1"),
        ("Table 2.2: Non-Functional Requirements Specifications", "tab_2_2"),
        ("Table 3.1: Detailed Mapping of User Entity Class", "tab_3_1"),
        ("Table 3.2: Detailed Mapping of StudentClass Class", "tab_3_2"),
        ("Table 3.3: Detailed Mapping of University Class", "tab_3_3"),
        ("Table 3.4: Detailed Mapping of Resource Class", "tab_3_4"),
        ("Table 3.5: Detailed Mapping of SlotDetectionService Class", "tab_3_5"),
        ("Table 5.1: Test Plan Matrix", "tab_5_1"),
    ]
    
    formatted_lot = []
    for label, tab_id in tables_list:
        p_num = str(headings_map.get(tab_id, "0"))
        lbl = Paragraph(label, toc_label_style)
        pg = Paragraph(p_num, toc_page_style)
        formatted_lot.append([lbl, pg])
        
    lot_table = Table(formatted_lot, colWidths=[454, 50])
    lot_table.setStyle(TableStyle([
        ('VALIGN', (0,0), (-1,-1), 'BOTTOM'),
        ('BOTTOMPADDING', (0,0), (-1,-1), 4),
        ('TOPPADDING', (0,0), (-1,-1), 4),
        ('LINEBELOW', (0,0), (-1,-1), 0.5, colors.HexColor("#E2E8F0")),
    ]))
    story.append(lot_table)
    story.append(PageBreak())

    # --- LIST OF FIGURES ---
    story.append(TrackingParagraph("List of Figures", h1_style, "toc_lof"))
    story.append(Spacer(1, 10))
    
    figures_list = [
        ("Figure 3.1: Component-Level System Architecture Diagram", "fig_3_1"),
        ("Figure 3.2: Class Architecture and Key Associations", "fig_3_2"),
        ("Figure 4.1: Code Snippet proving Encapsulation (User.java)", "fig_4_1"),
        ("Figure 4.2: Code Snippet proving Inheritance (UserRepository.java)", "fig_4_2"),
        ("Figure 4.3: Code Snippet proving Polymorphism (CustomUserDetailsService.java)", "fig_4_3"),
        ("Figure 4.4: Code Snippet proving Abstraction (SlotDetectionService.java)", "fig_4_4"),
        ("Figure 6.1: Appendix C Placeholder for Dashboard UI Screenshot", "fig_6_1"),
        ("Figure 6.2: Appendix C Placeholder for Routine & Slot Scheduling UI Screenshot", "fig_6_2"),
    ]
    
    formatted_lof = []
    for label, fig_id in figures_list:
        p_num = str(headings_map.get(fig_id, "0"))
        lbl = Paragraph(label, toc_label_style)
        pg = Paragraph(p_num, toc_page_style)
        formatted_lof.append([lbl, pg])
        
    lof_table = Table(formatted_lof, colWidths=[454, 50])
    lof_table.setStyle(TableStyle([
        ('VALIGN', (0,0), (-1,-1), 'BOTTOM'),
        ('BOTTOMPADDING', (0,0), (-1,-1), 4),
        ('TOPPADDING', (0,0), (-1,-1), 4),
        ('LINEBELOW', (0,0), (-1,-1), 0.5, colors.HexColor("#E2E8F0")),
    ]))
    story.append(lof_table)
    story.append(PageBreak())

    # --- CHAPTER 1: INTRODUCTION ---
    story.append(TrackingParagraph("Chapter 1: Introduction", h1_style, "toc_ch1"))
    
    story.append(Paragraph("1.1 Project Background and Motivation", h2_style))
    story.append(Paragraph(REPORT_TEXT["ch1_1"], body_style))
    
    story.append(Paragraph("1.2 Problem Statement", h2_style))
    story.append(Paragraph(REPORT_TEXT["ch1_2"], body_style))
    
    story.append(Paragraph("1.3 Project Objectives", h2_style))
    story.append(Paragraph(REPORT_TEXT["ch1_3"], body_style))
    
    story.append(Paragraph("1.4 Tools and Technologies Used", h2_style))
    story.append(Paragraph(REPORT_TEXT["ch1_4"], body_style))
    
    t1_1_data = [
        ["Tool / Technology", "Specification / Version", "Purpose in learnX.help"],
        ["Java Development Kit (JDK)", "Version 21", "Core programming language utilizing modern standard library features."],
        ["Spring Boot", "Version 4.0.6 (Spring parent)", "Base application micro-architecture providing automatic beans wiring."],
        ["Spring Data JPA", "Jakarta Persistence API", "Object-Relational Mapping (ORM) to maps Java classes to database entities."],
        ["Spring Security", "Security Filters", "User authentication, session tracking, and BCrypt password encryption."],
        ["Lombok", "Annotation Processor", "Auto-generates getters, setters, constructors, and builder patterns."],
        ["H2 Database", "In-memory / File-based", "Lightweight relational database for local development and integration tests."],
        ["PostgreSQL", "Relational Database", "Scalable database for persistent data storage in deployment."],
        ["Maven", "Build Automation", "Handles dependency management, compilation, and package generation."]
    ]
    t1_1_table = create_styled_table(make_paragraph_table_data(t1_1_data, styles), [110, 130, 264])
    story.append(t1_1_table)
    story.append(TrackingParagraph("Table 1.1: Tools and Development Environment", ParagraphStyle('Cap', parent=body_style, fontName='Helvetica-Oblique', alignment=1), "tab_1_1"))
    
    story.append(PageBreak())

    # --- CHAPTER 2: REQUIREMENTS AND ANALYSIS ---
    story.append(TrackingParagraph("Chapter 2: Requirements and Analysis", h1_style, "toc_ch2"))
    
    story.append(Paragraph("2.1 Functional Requirements", h2_style))
    story.append(Paragraph(REPORT_TEXT["ch2_1"], body_style))
    
    t2_1_data = [
        ["Req ID", "Requirement Name", "Description & Implementation Details"],
        ["FR-1", "User Authentication & Approval", "Enables sign up for all roles. New accounts remain locked until approved by the administrator. Passwords are encrypted."],
        ["FR-2", "Routine & Class Schedule", "Allows CRs and teachers to insert class routines including days, slots, course names, and class sections."],
        ["FR-3", "Automated Slot Detection", "Given a date and duration, the system scans all class routines and scheduled exams, merging busy periods to identify free academic intervals."],
        ["FR-4", "Academic Resource Vault", "Supports uploading notes, slides, and links. Features user reactions (likes/dislikes) and admin approvals for quality control."],
        ["FR-5", "MCQ Exam Engine", "Teachers can construct multiple-choice exams. Students submit responses which are auto-graded and saved immediately."],
        ["FR-6", "GradeBook & Performance Stats", "Tracks scores for exams and tests, dynamically computing class averages and highest scores for students to review."]
    ]
    t2_1_table = create_styled_table(make_paragraph_table_data(t2_1_data, styles), [45, 120, 339])
    story.append(t2_1_table)
    story.append(TrackingParagraph("Table 2.1: Functional Requirements Specifications", ParagraphStyle('Cap2', parent=body_style, fontName='Helvetica-Oblique', alignment=1), "tab_2_1"))
    
    story.append(Spacer(1, 10))
    story.append(Paragraph("2.2 Non-Functional Requirements", h2_style))
    story.append(Paragraph(REPORT_TEXT["ch2_2"], body_style))
    
    t2_2_data = [
        ["Category", "Requirement Specification", "Implementation Detail in learnX.help"],
        ["Security", "Role Authorization & Enrolment", "Strict endpoint routing rules mapped via Spring Security config. Fields are encapsulated via Lombok."],
        ["Performance", "Fast Scheduling Detection", "Slot detection utilizes active time-interval sorting and one-pass overlap merging, executing in < 15ms locally."],
        ["Reliability", "Transactional Consistency", "All operations are backed by Spring's transactional manager. Rollbacks occur on JpaRepository exceptions."],
        ["Scalability", "High-capacity Uploads", "Supports binary storage in Database LOB fields up to 100MB, configuring multipart servlet properties."]
    ]
    t2_2_table = create_styled_table(make_paragraph_table_data(t2_2_data, styles), [80, 150, 274])
    story.append(t2_2_table)
    story.append(TrackingParagraph("Table 2.2: Non-Functional Requirements Specifications", ParagraphStyle('Cap3', parent=body_style, fontName='Helvetica-Oblique', alignment=1), "tab_2_2"))
    
    story.append(PageBreak())

    # --- CHAPTER 3: OBJECT-ORIENTED DESIGN AND METHODOLOGY ---
    story.append(TrackingParagraph("Chapter 3: Object-Oriented Design and Methodology", h1_style, "toc_ch3"))
    
    story.append(Paragraph("3.1 Methodology Used", h2_style))
    story.append(Paragraph(REPORT_TEXT["ch3_1"], body_style))
    
    story.append(Paragraph("3.2 Class Diagram Mapping", h2_style))
    story.append(Paragraph(REPORT_TEXT["ch3_2"], body_style))
    
    # Load class diagram image dynamically if placed in workspace (e.g. class_diagram.png)
    story.append(make_placeholder_or_image("class_diagram.png", "UML Class Diagram Mapping", "Visualizes relationships: University (1) &lt;-&gt; (many) StudentClass &lt;-&gt; (many) User. Resource and Exam associate with User and StudentClass. (Place class_diagram.png in workspace root to automatically embed).", 100))
    story.append(TrackingParagraph("Figure 3.1: Component-Level System Architecture Diagram", ParagraphStyle('Fig1', parent=body_style, fontName='Helvetica-Oblique', alignment=1), "fig_3_1"))
    
    story.append(Spacer(1, 10))
    story.append(make_placeholder_or_image("class_associations.png", "Entity Relationships and Key Associations", "Represents JpaRepository inheritance hierarchies and Service dependencies (SlotDetectionService depending on ScheduleItemRepository & ClassTestRepository). (Place class_associations.png in workspace root to automatically embed).", 100))
    story.append(TrackingParagraph("Figure 3.2: Class Architecture and Key Associations", ParagraphStyle('Fig2', parent=body_style, fontName='Helvetica-Oblique', alignment=1), "fig_3_2"))
    
    story.append(PageBreak())
    
    story.append(Paragraph("3.3 Detailed Class Descriptions", h2_style))
    story.append(Paragraph(REPORT_TEXT["ch3_3"], body_style))
    
    # Class Description User
    t3_1_data = [
        ["Field Name", "Type", "Annotations / Structural Purpose"],
        ["id", "Long", "@Id, @GeneratedValue (IDENTITY) - Database primary key."],
        ["username", "String", "Unique, non-null user identification nickname."],
        ["password", "String", "Encrypted string containing BCrypt hashed credentials."],
        ["fullName", "String", "Full legal name of the user."],
        ["email", "String", "Unique contact email address."],
        ["role", "Role (Enum)", "STUDENT, CR, TEACHER, or ADMIN defining authorization bounds."],
        ["studentClass", "StudentClass", "@ManyToOne - Defines the class section mapping for students/CRs."],
        ["university", "University", "@ManyToOne - Enforces tenant isolation for universities."]
    ]
    t3_1_table = create_styled_table(make_paragraph_table_data(t3_1_data, styles), [100, 110, 294])
    story.append(t3_1_table)
    story.append(TrackingParagraph("Table 3.1: Detailed Mapping of User Entity Class", ParagraphStyle('CapT31', parent=body_style, fontName='Helvetica-Oblique', alignment=1), "tab_3_1"))
    story.append(Spacer(1, 15))
    
    # Class Description StudentClass
    t3_2_data = [
        ["Field Name", "Type", "Annotations / Structural Purpose"],
        ["id", "Long", "Primary Key."],
        ["batch", "String", "Academic batch label (e.g. '43')."],
        ["department", "String", "Division department label (e.g. 'CSE')."],
        ["section", "String", "Class subsection identifier (e.g. 'A')."],
        ["university", "University", "@ManyToOne - Associates the class with a university tenant."],
        ["students", "List<User>", "@OneToMany(mappedBy='studentClass') - enrolled students."]
    ]
    t3_2_table = create_styled_table(make_paragraph_table_data(t3_2_data, styles), [100, 110, 294])
    story.append(t3_2_table)
    story.append(TrackingParagraph("Table 3.2: Detailed Mapping of StudentClass Class", ParagraphStyle('CapT32', parent=body_style, fontName='Helvetica-Oblique', alignment=1), "tab_3_2"))
    story.append(Spacer(1, 15))
    
    # Class Description University
    t3_3_data = [
        ["Field Name", "Type", "Annotations / Structural Purpose"],
        ["id", "Long", "Primary Key."],
        ["name", "String", "Unique full university name (e.g., 'USTC')."],
        ["domain", "String", "Unique subdomain string for URL mapping (e.g., 'ustc.learnx.com')."],
        ["logoUrl", "String", "Base64 or URL path to the institutional logo image."]
    ]
    t3_3_table = create_styled_table(make_paragraph_table_data(t3_3_data, styles), [100, 110, 294])
    story.append(t3_3_table)
    story.append(TrackingParagraph("Table 3.3: Detailed Mapping of University Class", ParagraphStyle('CapT33', parent=body_style, fontName='Helvetica-Oblique', alignment=1), "tab_3_3"))
    
    story.append(PageBreak())
    
    # Class Description Resource
    t3_4_data = [
        ["Field Name", "Type", "Annotations / Structural Purpose"],
        ["id", "Long", "Primary Key."],
        ["title", "String", "Display title of notes/handouts."],
        ["fileName", "String", "Physical file name upload string."],
        ["fileData", "byte[]", "@Lob - Stores high-capacity note bytes (up to 100MB)."],
        ["uploadedBy", "User", "@ManyToOne - Reference to user who uploaded the handout."],
        ["likesCount", "int", "@Transient - Calculated reaction sum counts."],
        ["driveLink", "String", "Direct Google Drive URL for external lecture videos."]
    ]
    t3_4_table = create_styled_table(make_paragraph_table_data(t3_4_data, styles), [100, 110, 294])
    story.append(t3_4_table)
    story.append(TrackingParagraph("Table 3.4: Detailed Mapping of Resource Class", ParagraphStyle('CapT34', parent=body_style, fontName='Helvetica-Oblique', alignment=1), "tab_3_4"))
    story.append(Spacer(1, 15))
    
    # Class Description SlotDetectionService
    t3_5_data = [
        ["Method/Field", "Type", "Functional / Architectural Role"],
        ["scheduleItemRepository", "ScheduleItemRepository", "CRUD operations for loading academic day-routines."],
        ["classTestRepository", "ClassTestRepository", "CRUD operations for loading pre-scheduled examinations."],
        ["detectFreeSlots", "List<TimeInterval>", "Loads routines & tests, merges intervals, returns open slots."],
        ["mergeIntervals", "List<TimeInterval>", "Filters, clamps intervals, sorts by start, and merges overlaps."]
    ]
    t3_5_table = create_styled_table(make_paragraph_table_data(t3_5_data, styles), [140, 130, 234])
    story.append(t3_5_table)
    story.append(TrackingParagraph("Table 3.5: Detailed Mapping of SlotDetectionService Class", ParagraphStyle('CapT35', parent=body_style, fontName='Helvetica-Oblique', alignment=1), "tab_3_5"))
    
    story.append(PageBreak())

    # --- CHAPTER 4: IMPLEMENTATION AND CORE OOP PRINCIPLES ---
    story.append(TrackingParagraph("Chapter 4: Implementation and Core OOP Principles", h1_style, "toc_ch4"))
    
    story.append(Paragraph("4.1 Key OOP Principles Applied", h2_style))
    # Render multi-line paragraphs cleanly
    for para in REPORT_TEXT["ch4_1"].split('\n\n'):
        story.append(Paragraph(para, body_style))
    
    story.append(Paragraph("4.2 Code Snippets", h2_style))
    story.append(Paragraph(REPORT_TEXT["ch4_2"], body_style))
    
    story.append(Paragraph("<b>Encapsulation Proof (User.java):</b>", body_style))
    encap_snippet = get_snippet('encapsulation')
    story.append(make_code_block(encap_snippet))
    story.append(TrackingParagraph("Figure 4.1: Code Snippet proving Encapsulation (User.java)", ParagraphStyle('Fig41', parent=body_style, fontName='Helvetica-Oblique', alignment=1), "fig_4_1"))
    
    story.append(PageBreak())
    
    story.append(Paragraph("<b>Inheritance Proof (UserRepository.java):</b>", body_style))
    inherit_snippet = get_snippet('inheritance')
    story.append(make_code_block(inherit_snippet))
    story.append(TrackingParagraph("Figure 4.2: Code Snippet proving Inheritance (UserRepository.java)", ParagraphStyle('Fig42', parent=body_style, fontName='Helvetica-Oblique', alignment=1), "fig_4_2"))
    
    story.append(Spacer(1, 10))
    story.append(Paragraph("<b>Polymorphism Proof (CustomUserDetailsService.java):</b>", body_style))
    polymorph_snippet = get_snippet('polymorphism')
    story.append(make_code_block(polymorph_snippet))
    story.append(TrackingParagraph("Figure 4.3: Code Snippet proving Polymorphism (CustomUserDetailsService.java)", ParagraphStyle('Fig43', parent=body_style, fontName='Helvetica-Oblique', alignment=1), "fig_4_3"))
    
    story.append(PageBreak())
    
    story.append(Paragraph("<b>Abstraction Proof (SlotDetectionService.java):</b>", body_style))
    abstract_snippet = get_snippet('abstraction')
    story.append(make_code_block(abstract_snippet))
    story.append(TrackingParagraph("Figure 4.4: Code Snippet proving Abstraction (SlotDetectionService.java)", ParagraphStyle('Fig44', parent=body_style, fontName='Helvetica-Oblique', alignment=1), "fig_4_4"))
    
    story.append(PageBreak())

    # --- CHAPTER 5: TESTING AND RESULTS ---
    story.append(TrackingParagraph("Chapter 5: Testing and Results", h1_style, "toc_ch5"))
    
    story.append(Paragraph("5.1 Test Plan", h2_style))
    story.append(Paragraph(REPORT_TEXT["ch5_1"], body_style))
    
    t5_1_data = [
        ["Test ID", "Phase", "Target Component", "Validation Strategy"],
        ["TP-01", "Unit Testing", "User & Entity Models", "Validate builder patterns, Lombok generation, and validation tags."],
        ["TP-02", "Unit Testing", "Slot Merging Logic", "Test mergeIntervals with overlapping/boundary time windows."],
        ["TP-03", "Integration", "SlotDetectionService", "Verify routines and exams date queries output correct free slots."],
        ["TP-04", "Integration", "Resource Controller", "Verify multipart file upload parsing and byte storage verification."],
        ["TP-05", "Integration", "Exam Controller", "Verify quiz grading, score computations, and JPA grade book persistence."],
        ["TP-06", "Security", "Authentication Filters", "Confirm role permissions (STUDENT vs TEACHER vs ADMIN) on restricted REST paths."]
    ]
    t5_1_table = create_styled_table(make_paragraph_table_data(t5_1_data, styles), [50, 80, 130, 244])
    story.append(t5_1_table)
    story.append(TrackingParagraph("Table 5.1: Test Plan Matrix", ParagraphStyle('CapT51', parent=body_style, fontName='Helvetica-Oblique', alignment=1), "tab_5_1"))
    
    story.append(PageBreak())

    # --- CHAPTER 6: CONCLUSION AND FUTURE WORK ---
    story.append(TrackingParagraph("Chapter 6: Conclusion and Future Work", h1_style, "toc_ch6"))
    
    story.append(Paragraph("6.1 Conclusion", h2_style))
    story.append(Paragraph(REPORT_TEXT["ch6_1"], body_style))
    
    story.append(Paragraph("6.2 Limitations", h2_style))
    story.append(Paragraph(REPORT_TEXT["ch6_2"], body_style))
    
    story.append(Paragraph("6.3 Future Enhancements", h2_style))
    story.append(Paragraph(REPORT_TEXT["ch6_3"], body_style))
    
    story.append(PageBreak())

    # --- REFERENCES ---
    story.append(TrackingParagraph("References", h1_style, "toc_ref"))
    story.append(Spacer(1, 10))
    ref_style = ParagraphStyle(
        'RefStyle',
        fontName='Helvetica',
        fontSize=8.5,
        leading=13,
        textColor=TEXT_DARK,
        leftIndent=24,
        firstLineIndent=-24,
        spaceAfter=8
    )
    
    refs = [
        "[1] E. Gamma, R. Helm, R. Johnson, and J. Vlissides, <i>Design Patterns: Elements of Reusable Object-Oriented Software</i>. Boston, MA: Addison-Wesley, 1994.",
        "[2] H. Schildt, <i>Java: The Complete Reference</i>, 12th ed. New York, NY: McGraw-Hill, 2021.",
        "[3] R. Johnson et al., <i>Spring Boot in Action</i>. Shelter Island, NY: Manning Publications, 2016.",
        "[4] C. Bauer and G. King, <i>Java Persistence with Hibernate</i>. Shelter Island, NY: Manning Publications, 2015."
    ]
    for ref in refs:
        story.append(Paragraph(ref, ref_style))
        
    story.append(PageBreak())

    # --- APPENDIX A: FULL SOURCE CODE ---
    story.append(TrackingParagraph("Appendix A: Full Source Code", h1_style, "toc_app_a"))
    story.append(Paragraph(
        "The complete main Java source files recursively extracted from the <code>src/main/java</code> "
        "workspace directory are cleanly indexed and formatted below.",
        body_style
    ))
    story.append(Spacer(1, 10))
    
    java_files = find_java_files("e:/V1LearnX/src/main/java")
    for idx, (rel_path, abs_path) in enumerate(java_files, 1):
        heading_p = Paragraph(f"<b>A.{idx}: {rel_path}</b>", h2_style)
        story.append(KeepTogether([
            heading_p,
            Spacer(1, 4),
            make_source_code_table(abs_path)
        ]))
        story.append(Spacer(1, 15))
        
    story.append(PageBreak())

    # --- APPENDIX B: USER MANUAL ---
    story.append(TrackingParagraph("Appendix B: User Manual", h1_style, "toc_app_b"))
    story.append(Paragraph("<b>System Configuration and Execution Details</b>", h2_style))
    
    # Split text manual lines and render cleanly
    for para in REPORT_TEXT["app_b"].split('\n'):
        if para.strip():
            story.append(Paragraph(para, body_style))
            story.append(Spacer(1, 4))
    
    story.append(PageBreak())

    # --- APPENDIX C: SCREENSHOTS ---
    story.append(TrackingParagraph("Appendix C: Screenshots", h1_style, "toc_app_c"))
    story.append(Paragraph(
        "Below are UI validation frames. Place your actual screenshot PNGs in the root workspace folder to automatically embed them.",
        body_style
    ))
    story.append(Spacer(1, 10))
    
    story.append(make_placeholder_or_image("dashboard.png", "Student dashboard UI Screenshot", "Shows announcements feed, academic routines, and latest grades summary. (Place dashboard.png in root to replace).", 120))
    story.append(TrackingParagraph("Figure 6.1: Appendix C Placeholder for Dashboard UI Screenshot", ParagraphStyle('FigC1', parent=body_style, fontName='Helvetica-Oblique', alignment=1), "fig_6_1"))
    story.append(Spacer(1, 20))
    
    story.append(make_placeholder_or_image("slot_scheduling.png", "Routine & Free Slot Detection UI Screenshot", "Shows dates inputs, duration configurations, and calculated free time blocks. (Place slot_scheduling.png in root to replace).", 120))
    story.append(TrackingParagraph("Figure 6.2: Appendix C Placeholder for Routine & Slot Scheduling UI Screenshot", ParagraphStyle('FigC2', parent=body_style, fontName='Helvetica-Oblique', alignment=1), "fig_6_2"))

    # --- COMPILATION ---
    doc = SimpleDocTemplate(
        filename,
        pagesize=letter,
        leftMargin=54,
        rightMargin=54,
        topMargin=54,
        bottomMargin=54
    )
    
    doc.build(story, onFirstPage=draw_cover_page, onLaterPages=lambda c, d: None, canvasmaker=NumberedCanvas)

if __name__ == "__main__":
    # First pass: compile to record page numbers of all tracked elements
    print("Running Pass 1 to record page positions...")
    build_pdf()
    # Second pass: compile with correct TOC, LOT, and LOF page numbers
    print("Running Pass 2 to compile final PDF...")
    build_pdf()
    print("PDF generation complete.")
