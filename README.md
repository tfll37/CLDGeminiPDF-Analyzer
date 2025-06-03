# CLDGeminiPDF MCP Server

A Model Context Protocol (MCP) server that enables Claude Desktop to analyze PDF documents using Google's AI models. This server sends PDF files & extracts text from PDFs by leveraging Gemini's powerful language understanding to provide intelligent analysis and insights.

## Quick Start

1. **Download**: Get the pre-built JAR from [Releases](../../releases)
2. **API Key**: Get your free Gemini API key from [Google AI Studio](https://aistudio.google.com)
3. **Configure**: Add the server to your Claude Desktop config with the JAR path and API key
4. **Analyze**: Start analyzing PDFs with Claude!

## Features

- **PDF Analysis**: Extract and analyze PDF content using Gemini AI models
- **Multiple Model Support**: Choose from various Gemini models (2.5, 2.0, 1.5 series, and Gemma models)
- **Dual Processing Methods**: Direct PDF upload to Gemini or fallback text extraction
- **MCP Integration**: Seamless integration with Claude Desktop via Model Context Protocol
- **Flexible Configuration**: Environment-based configuration for easy deployment

## Prerequisites

- **Java 11 or higher**
- **Maven** (for building from source)
- **Google AI Studio API Key** (free at [aistudio.google.com](https://aistudio.google.com))
- **Claude Desktop** application
- **Filesystem MCP Server** (required dependency)

## Installation

### Option 1: Download Pre-built JAR (Recommended)

1. Download the latest `CLDGeminiPDF.v1.0.0.jar` from the [Releases](../../releases) page
2. Save it to a convenient location on your system

### Option 2: Build from Source

```bash
git clone <your-repository-url>
cd CLDGeminiPDF
mvn clean compile assembly:single
```

This will create a JAR file with all dependencies included in the `target/` directory.

### Get Your Gemini API Key

1. Visit [Google AI Studio](https://aistudio.google.com)
2. Sign in with your Google account
3. Click "Get API Key" and create a new key
4. Copy the API key for use in environment variables

## Claude Desktop Integration

### 1. Locate Claude Desktop Configuration

The configuration file location depends on your operating system:

- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`
- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Linux**: `~/.config/Claude/claude_desktop_config.json`

### 2. Update Configuration File

Add both the filesystem server and CLDGeminiPDF server to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "npx",
      "args": [
        "-y",
        "@modelcontextprotocol/server-filesystem",
        "/path/to/your/documents"
      ]
    },
    "CLDGeminiPDF": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/CLDGeminiPDF.v1.0.0.jar"
      ],
      "env": {
        "GEMINI_API_KEY": "your_api_key_here",
        "GEMINI_MODEL": "gemini-2.0-flash"
      }
    }
  }
}
```

**Important**: 
- Replace `/path/to/your/documents` with the directory containing your PDF files
- Replace `/path/to/CLDGeminiPDF.v1.0.0.jar` with the actual path to your downloaded JAR file
- Replace `your_api_key_here` with your actual Gemini API key

#### Example Paths:
- **Windows**: `"C:\\Users\\YourName\\Downloads\\CLDGeminiPDF.v1.0.0.jar"`
- **macOS**: `"/Users/YourName/Downloads/CLDGeminiPDF.v1.0.0.jar"`
- **Linux**: `"/home/yourusername/Downloads/CLDGeminiPDF.v1.0.0.jar"`

### 3. Restart Claude Desktop

Close and restart Claude Desktop for the changes to take effect.

## Available Gemini Models

*Data for Free Tier Users of Google API*

| Model                            | Description                            | Limits                         |
|----------------------------------|----------------------------------------|--------------------------------|
| `gemini-2.5-flash-preview-05-20` | Latest preview with high performance   | 10 RPM, 250K TPM               |
| `gemini-2.5-flash-preview-04-17` | Previous preview version               | 10 RPM, 250K TPM               |
| `gemini-2.5-pro-preview-05-06`   | Pro version with advanced capabilities | Limited availability           |
| `gemini-2.0-flash`               | Stable, fast model (default)           | 15 RPM, 1M TPM                 |
| `gemini-2.0-flash-lite`          | Lightweight version                    | 30 RPM, 1M TPM                 |
| `gemini-1.5-flash`               | Stable general-purpose model           | 15 RPM, 250K TPM               |
| `gemini-1.5-pro`                 | Largest context window of 2M tokens    | Unavailable for free tier users |
| `gemma-3 models`                 | Open models, up to 27B parameters      | 30 RPM, 15K TPM                |

*Model availability depends on your API key and usage tier. Check your Google AI Studio account for available models.*

*Data sourced from https://ai.google.dev/gemini-api/docs/rate-limits#free-tier*

## Usage Examples

The CLDGeminiPDF MCP server requires the [Filesystem MCP server](https://github.com/modelcontextprotocol/servers/tree/dd025e34ef2c03fff86b38f4106c231c24d05a15/src/filesystem) to access PDF files. It works with complete file paths or by finding files within the allowed directory scope.

**Note**: Version 1.0.0 does NOT support drag-and-drop files in the chat interface. Files must be accessible through the filesystem server.

Once configured, you can use these commands in Claude Desktop:

### Analyze a PDF with Full Path
```
Please analyze this research paper: file:///Users/username/Documents/research_paper.pdf

Focus on the methodology and conclusions.
```

### Find and Analyze a PDF by Name
```
Find the "research_paper.pdf" file in the Documents directory and analyze it using Gemini.
```

### List Available Models
```
What Gemini models are available for PDF analysis?
```

### Use a Specific Model
```
Analyze this contract using the gemini-2.5-pro model: file:///path/to/contract.pdf

Look for key terms and potential risks.
```

## Troubleshooting

### Common Issues

1. **"GEMINI_API_KEY environment variable is not set!"**
   - Ensure your API key is properly set in the Claude Desktop configuration
   - Restart Claude Desktop after updating the configuration

2. **"PDF file not found or not readable"**
   - Verify the file path is correct and within the filesystem server's allowed directory
    - Ensure the PDF file exists and is readable
    - Check file permissions

3. **"Gemini API error: 401"**
    - Verify your API key is correct and valid
   - Check if your API key has the necessary permissions in Google AI Studio

4. **"Gemini API error: 429"**
    - You've hit rate limits. Wait and try again
   - Consider using a model with higher rate limits (see table above)

5. **Filesystem server not working**
   - Ensure the filesystem MCP server is properly configured
   - Check that the directory path in the filesystem configuration is correct
   - Verify Claude Desktop has permission to access the specified directory

### Debugging

To enable detailed logging, you can modify the Java command in your configuration:

```json
"args": [
  "-Djava.util.logging.level=INFO",
  "-jar",
  "/path/to/CLDGeminiPDF.v1.0.0.jar"
]
```

### Verify Configuration

Test your setup by asking Claude Desktop:
```
List the available Gemini models
```

If successful, you should see a JSON response with available models and their capabilities.

## Development

### Building from Source

```bash
git clone <your-repository-url>
cd CLDGeminiPDF
mvn clean compile assembly:single
```

The JAR file will be created in the `target/` directory.

### Dependencies

- Jackson (JSON processing)
- Apache PDFBox (PDF text extraction)
- MCP Java SDK (Model Context Protocol)
- Java HTTP Client (API communication)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues and questions:
- Check the troubleshooting section above
- Review [Claude Desktop MCP documentation](https://docs.anthropic.com/en/docs/build-with-claude/mcp)
- Check [Google AI Studio API documentation](https://ai.google.dev/docs)
- Open an issue on this repository