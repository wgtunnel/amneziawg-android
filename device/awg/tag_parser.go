package awg

import (
	"fmt"
	"maps"
	"regexp"
	"strings"
)

type EnumTag string

const (
	BytesEnumTag        EnumTag = "b"
	CounterEnumTag      EnumTag = "c"
	TimestampEnumTag    EnumTag = "t"
	RandomBytesEnumTag  EnumTag = "r"
	WaitTimeoutEnumTag  EnumTag = "wt"
	WaitResponseEnumTag EnumTag = "wr"
)

var generatorCreator = map[EnumTag]newGenerator{
	BytesEnumTag:        newBytesGenerator,
	CounterEnumTag:      func(s string) (Generator, error) { return &BytesGenerator{}, nil },
	TimestampEnumTag:    newTimestampGenerator,
	RandomBytesEnumTag:  newRandomPacketGenerator,
	WaitTimeoutEnumTag:  newWaitTimeoutGenerator,
	WaitResponseEnumTag: func(s string) (Generator, error) { return &BytesGenerator{}, nil },
}

// helper map to determine enumTags are unique
var uniqueTags = map[EnumTag]bool{
	CounterEnumTag:   false,
	TimestampEnumTag: false,
}

type Tag struct {
	Name  EnumTag
	Param string
}

func parseTag(input string) (Tag, error) {
	// Regular expression to match <tagname optional_param>
	re := regexp.MustCompile(`([a-zA-Z]+)(?:\s+([^>]+))?>`)

	match := re.FindStringSubmatch(input)
	tag := Tag{
		Name: EnumTag(match[1]),
	}
	if len(match) > 2 && match[2] != "" {
		tag.Param = strings.TrimSpace(match[2])
	}

	return tag, nil
}

// TODO: pointernes
func Parse(name, input string) (TagJunkGenerator, error) {
	inputSlice := strings.Split(input, "<")
	if len(inputSlice) <= 1 {
		return TagJunkGenerator{}, fmt.Errorf("empty input: %s", input)
	}

	uniqueTagCheck := make(map[EnumTag]bool, len(uniqueTags))
	maps.Copy(uniqueTagCheck, uniqueTags)

	// skip byproduct of split
	inputSlice = inputSlice[1:]
	rv := newTagJunkGenerator(name, len(inputSlice))
	for _, inputParam := range inputSlice {
		if len(inputParam) <= 1 {
			return TagJunkGenerator{}, fmt.Errorf("empty tag in input: %s", inputSlice)
		} else if strings.Count(inputParam, ">") != 1 {
			return TagJunkGenerator{}, fmt.Errorf("ill formated input: %s", input)
		}

		tag, _ := parseTag(inputParam)
		creator, ok := generatorCreator[tag.Name]
		if !ok {
			return TagJunkGenerator{}, fmt.Errorf("invalid tag: %s", tag.Name)
		}
		if present, ok := uniqueTagCheck[tag.Name]; ok {
			if present {
				return TagJunkGenerator{}, fmt.Errorf("tag %s needs to be unique", tag.Name)
			}
			uniqueTagCheck[tag.Name] = true
		}
		generator, err := creator(tag.Param)
		if err != nil {
			return TagJunkGenerator{}, fmt.Errorf("gen: %w", err)
		}

		rv.append(generator)
	}

	return rv, nil
}
